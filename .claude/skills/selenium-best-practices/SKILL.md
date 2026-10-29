---
name: selenium-best-practices
description: Selenium WebDriver (Core Java + TestNG) E2E test automation standards for EventHub — project setup, locator strategy, explicit waits, assertion patterns, test structure, POM, auth via localStorage, native dialogs, API setup/cleanup with java.net.http, and anti-patterns. Use when writing, reviewing, or debugging Selenium tests.
user-invocable: false
---

# Selenium WebDriver (Java) Test Automation Best Practices for EventHub

## Overview
This document defines the testing standards, patterns, and best practices for writing Selenium WebDriver E2E tests in **Core Java** for the EventHub project. All test automation agents and code reviewers MUST follow these guidelines.

Playwright is the project's primary E2E tool (see `playwright-best-practices`). Use Selenium when a task explicitly asks for it, or when driving the browser through the Selenium MCP server.

Loaded by `/generate-tests-selenium-java` (writes and runs Selenium Java tests), `/review-tests-selenium-java` (reviews them) and `/test-strategy`. Both best-practices skills share the same locator priority, shared-account rules and EventHub domain facts. If you change a rule here, check whether `playwright-best-practices` needs the same change.

Domain knowledge lives in the `eventhub-domain` skill: `ui-selectors.md` for selectors (written in Playwright syntax; §2 shows the `By` equivalents), `business-rules.md` for what to assert, `user-flows.md` for test steps and test accounts, `api-reference.md` for API setup and cleanup (§8).

"Core Java" here means plain Java and the JDK: no Spring, no Serenity, no Cucumber, no RestAssured. The only libraries are Selenium, TestNG, and Jackson (the JDK has no JSON parser).

---

## 1. Project Test Setup

### Stack
- **Language**: Java 17+ (records, `var` and text blocks are fine)
- **Build**: Maven, as a separate module in `selenium/` with its own `pom.xml`
- **Driver**: `selenium-java` 4.x. Selenium Manager downloads the matching ChromeDriver automatically. Don't use WebDriverManager or commit driver binaries
- **Runner and assertions**: TestNG (`org.testng.Assert`, `SoftAssert`)
- **HTTP for API setup**: `java.net.http.HttpClient` (JDK) + Jackson `ObjectMapper`
- **Browser**: Chrome only (matches the Playwright setup)

### Dependencies (`selenium/pom.xml`)
```xml
<properties>
  <maven.compiler.release>17</maven.compiler.release>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
  <dependency>
    <groupId>org.seleniumhq.selenium</groupId>
    <artifactId>selenium-java</artifactId>
    <version>4.25.0</version>
  </dependency>
  <dependency>
    <groupId>org.testng</groupId>
    <artifactId>testng</artifactId>
    <version>7.10.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.2</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <version>3.5.0</version>
      <configuration>
        <suiteXmlFiles><suiteXmlFile>testng.xml</suiteXmlFile></suiteXmlFiles>
      </configuration>
    </plugin>
  </plugins>
</build>
```
Pin exact versions. Never use version ranges or `LATEST`.

### Config Reference
- **Base URL**: `https://eventhub.rahulshettyacademy.com` (live site; tests do not hit the local dev server)
- **API URL**: `https://api.eventhub.rahulshettyacademy.com/api` (the site's own `/api` path serves the Next.js app, not the backend)
- **Timeouts**: 5s default explicit wait, 30s page load
- **Parallel execution**: Disabled within the suite (`parallel="none"` in `testng.xml`), because all classes share Selenium's test users. The Selenium suite as a whole can run alongside Playwright, which uses different users (see Test Users)
- **Screenshots**: Only on failure (see §11)

```java
// src/test/java/com/eventhub/config/Config.java
public final class Config {
    private Config() {}

    public static final String BASE_URL = System.getProperty("baseUrl", "https://eventhub.rahulshettyacademy.com");
    public static final String API_URL  = System.getProperty("apiUrl",  "https://api.eventhub.rahulshettyacademy.com/api");
    public static final boolean HEADED  = Boolean.getBoolean("headed");
    public static final Duration WAIT   = Duration.ofSeconds(5);

    // Selenium's own users (not Playwright's), so the two suites can run in parallel
    public static final String USER_EMAIL    = env("SELENIUM_USER_EMAIL", "eventhub-selenium-main@qa-sandbox.dev");
    public static final String USER_PASSWORD = env("SELENIUM_USER_PASSWORD", "Selenium1!");
    // ... OTHER_USER_EMAIL / OTHER_USER_PASSWORD likewise, and env(name, fallback) reads System.getenv
}
```

### Driver Factory
```java
// src/test/java/com/eventhub/base/DriverFactory.java
public final class DriverFactory {
    private DriverFactory() {}

    public static WebDriver create() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1366,900");
        if (!Config.HEADED) options.addArguments("--headless=new");
        options.setCapability("goog:loggingPrefs", Map.of("browser", "ALL"));   // for console logs (§11)

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        // Implicit wait stays at 0 (the default). Use explicit waits only (§4)
        return driver;
    }
}
```
- Create one driver per test (`@BeforeMethod`) and quit it in `@AfterMethod(alwaysRun = true)`, so Chrome closes even when the test fails
- Run headed with `-Dheaded=true`

### Project Layout and Naming
```
selenium/
├── pom.xml
├── testng.xml
└── src/test/java/com/eventhub/
    ├── config/   Config.java
    ├── base/     DriverFactory.java, BaseTest.java
    ├── api/      ApiClient.java            (login, create/delete bookings)
    ├── pages/    BookingsPage.java, BookingDetailPage.java, EventDetailPage.java, AdminBookingsPage.java,
    │             LoginPage.java, Navbar.java, Toast.java
    ├── utils/    Locators.java, Screenshots.java, Visibility.java
    └── tests/    BookingFlowTest.java, BookingJourneyTest.java, AdminBookingsTest.java, AccessControlTest.java
```
Add new page objects and test classes next to these as features are covered.
- Keep Selenium code in `selenium/`, never under `tests/`, which belongs to Playwright (`testDir: ./tests`)
- Test classes: `<Feature>Test.java` in PascalCase (`BookingFlowTest`, `AccessControlTest`)
- Test methods: `tc<NNN>_<whatIsVerified>` in camelCase, with the full scenario title in `description`:
  ```java
  @Test(description = "TC-003: cancelling from the detail page shows a toast, redirects and deletes the booking")
  public void tc003_cancelFromDetailPage() { ... }
  ```
- One test class per feature; related tests stay together in that class

---

## 2. Locator Strategy (Priority Order)

Selenium has no role or label locators, so the priority list below is built around CSS. Same principle as Playwright: the most stable hook wins. `eventhub-domain/ui-selectors.md` lists the hooks in Playwright syntax: `getByTestId('x')` becomes `Locators.testId("x")`, `#id` stays `By.id("id")`, and `getByLabel`/`getByPlaceholder` become the input's id or a `[placeholder='...']` CSS selector. Put the helpers in one place:

```java
// src/test/java/com/eventhub/utils/Locators.java
public final class Locators {
    private Locators() {}

    public static By testId(String id)        { return By.cssSelector("[data-testid='" + id + "']"); }
    public static By buttonText(String text)  { return By.xpath("//button[normalize-space()='" + text + "']"); }
    public static By headingText(String text) { return By.xpath("//*[self::h1 or self::h2 or self::h3][normalize-space()='" + text + "']"); }
    public static By exactText(String text)   { return By.xpath("//*[normalize-space()='" + text + "']"); }
}
```

### Priority 1: data-testid (Most Preferred)
```java
driver.findElement(testId("booking-card"));
driver.findElement(testId("cancel-booking-btn"));
driver.findElement(testId("confirm-dialog-yes"));
```
Use for: Elements that need stable test hooks. Ask developers to add `data-testid` attributes.

### Priority 2: Element IDs
```java
driver.findElement(By.id("login-btn"));
driver.findElement(By.id("customer-email"));
driver.findElement(By.id("check-refund-btn"));
driver.findElement(By.id("ticket-count"));
```

### Priority 3: Accessibility attributes and form semantics
```java
driver.findElement(By.cssSelector("[role='dialog']"));
driver.findElement(By.cssSelector("input[placeholder='you@email.com']"));
driver.findElement(By.name("customerName"));
driver.findElement(By.cssSelector("button[type='submit']"));
```
Use for: Dialogs, inputs, landmarks (`main`, `nav`) — anything with a semantic attribute.

### Priority 4: Visible text (only when nothing above exists)
```java
driver.findElement(buttonText("Clear all bookings"));
driver.findElement(By.linkText("View Details"));       // exact link text, no XPath needed
driver.findElement(headingText("No bookings yet"));
```
Keep text XPaths short and always use `normalize-space()`.

### Priority 5: CSS Classes (Last Resort)
```java
driver.findElement(By.cssSelector(".booking-ref"));
driver.findElement(By.cssSelector(".confirm-booking-btn"));
```
Use for: Only when no better locator exists. Classes can change with styling.

### NEVER Use
- Absolute or positional XPath (`/html/body/div[2]/...`, `(//div)[3]`)
- Complex CSS chains (`.parent > .child:nth-child(3)`)
- Tailwind utility classes as hooks (`span.font-mono.font-bold`). They describe looks, not meaning, and match more than one element on the booking detail page
- `findElements(...).get(0)` without first filtering by content
- Copy-pasted "Copy XPath" output from DevTools

---

## 3. Filtering and Scoping Patterns

### Scope to a parent element
```java
WebElement card = driver.findElement(testId("booking-card"));
card.findElement(testId("cancel-booking-btn")).click();
```

### Find the card that contains a specific value
Selenium has no `filter({ hasText })`. Anchor on the `data-testid` and filter by content:
```java
public static By bookingCardFor(String bookingRef) {
    return By.xpath("//*[@data-testid='booking-card']"
        + "[.//*[contains(@class,'booking-ref') and normalize-space()='" + bookingRef + "']]");
}
```
This is the one allowed "structural" XPath: it anchors on a `data-testid` and filters by content, never by position.

### Check that something is absent
```java
// findElements never throws; it returns an empty list
wait.until(ExpectedConditions.numberOfElementsToBe(bookingCardFor(ref), 0));
```
Don't wrap `findElement` in `try/catch (NoSuchElementException)` to test for absence. It's slow (it waits for nothing) and hides real failures.

---

## 4. Wait Strategies

Most flaky Selenium tests come from waiting the wrong way. Selenium does **not** auto-wait like Playwright does, so every interaction with dynamic content needs an explicit wait.

### DO: Use `WebDriverWait` with `ExpectedConditions`
```java
WebDriverWait wait = new WebDriverWait(driver, Config.WAIT);

WebElement card = wait.until(ExpectedConditions.visibilityOfElementLocated(testId("booking-card")));
wait.until(ExpectedConditions.elementToBeClickable(testId("cancel-booking-btn"))).click();
wait.until(ExpectedConditions.urlToBe(Config.BASE_URL + "/bookings"));
wait.until(ExpectedConditions.urlMatches("/bookings/\\d+$"));
wait.until(ExpectedConditions.textToBe(By.cssSelector(".booking-ref"), bookingRef));
wait.until(ExpectedConditions.stalenessOf(card));               // element removed or re-rendered
wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("refund-spinner")));
```
Keep one `WebDriverWait` per test or page object (create it in `BaseTest` or the page constructor). Don't build a new one for every call.

### DO: Wait for a custom condition, with a message
```java
new WebDriverWait(driver, Config.WAIT)
    .withMessage("Expected the booking list to be empty")
    .until(d -> d.findElements(testId("booking-card")).isEmpty());
```
Build a new `WebDriverWait` when you add a message. `withMessage()` changes the instance it's called on, so on the shared `wait` the message would stick to every later wait.

### DO: Re-find elements after React re-renders
React Query refetches replace DOM nodes. If you hold a `WebElement` across an action that refreshes the list, you'll get `StaleElementReferenceException`. Locate it again; don't catch and retry.

### DON'T: Use arbitrary sleeps
```java
// BAD - Never do this
Thread.sleep(2000);
```

### DON'T: Use implicit waits
Leave `implicitlyWait` at 0. Mixing implicit and explicit waits produces unpredictable, compounding timeouts.

### DON'T: Use `FluentWait` to ignore every exception
Only ignore what's expected (`NoSuchElementException`, `StaleElementReferenceException`). Ignoring `Exception.class` hides real bugs.

### Exception: Timed UI (refund spinner)
```java
// The refund check shows a spinner for ~4s, so use a longer wait for this one step
driver.findElement(By.id("check-refund-btn")).click();
wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("refund-spinner")));
new WebDriverWait(driver, Duration.ofSeconds(6))
    .until(ExpectedConditions.invisibilityOfElementLocated(By.id("refund-spinner")));
WebElement result = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("refund-result")));
```

---

## 5. Assertion Patterns

Use TestNG's `Assert`. Remember its argument order is **`(actual, expected, message)`**, the reverse of JUnit. Selenium has no web-first assertions, so **wait first, then assert**.

### Visibility
```java
WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(exactText("Booking cancelled successfully")));
Assert.assertTrue(toast.isDisplayed(), "Success toast should be visible");
```

### URL
```java
wait.until(ExpectedConditions.urlToBe(Config.BASE_URL + "/bookings"));
Assert.assertEquals(driver.getCurrentUrl(), Config.BASE_URL + "/bookings");
```

### Content
```java
String cardText = card.getText();
Assert.assertTrue(cardText.contains(eventTitle), "Card should show the event title");
Assert.assertTrue(cardText.contains("2 tickets"), "Card should show the ticket count");
```

### Numeric values
```java
List<WebElement> cards = driver.findElements(testId("booking-card"));
Assert.assertEquals(cards.size(), 2, "Expected two booking cards");
Assert.assertEquals(seatsAfter, seatsBefore - 1);
```

### Business-rule format checks
```java
// Booking ref: <first letter of event title, uppercased>-<6 alphanumerics>
String prefix = String.valueOf(Character.toUpperCase(eventTitle.charAt(0)));
Assert.assertTrue(bookingRef.matches(Pattern.quote(prefix) + "-[A-Z0-9]{6}"),
    "Ref " + bookingRef + " should start with " + prefix + "-");
```

### Soft assertions for "check every field" steps
```java
SoftAssert soft = new SoftAssert();
soft.assertTrue(page.contains(event.venue()),       "venue");
soft.assertTrue(page.contains(CUSTOMER_NAME),       "customer name");
soft.assertTrue(page.contains("#" + booking.id()),  "booking id");
soft.assertAll();   // REQUIRED, or failures are silently dropped
```
Use soft asserts only for independent field checks. Steps that later steps depend on (navigation, the element existing) use hard asserts.

### Price formatting
Match the app's display: USD, no decimals.
```java
NumberFormat usd = NumberFormat.getCurrencyInstance(Locale.US);
usd.setMaximumFractionDigits(0);
String expectedTotal = usd.format(event.price() * quantity);   // e.g. "$600"
```

---

## 6. Test Structure Patterns

### Base Test
```java
// src/test/java/com/eventhub/base/BaseTest.java
public abstract class BaseTest {
    protected WebDriver driver;
    protected WebDriverWait wait;
    protected ApiClient api;
    protected String token;
    protected final List<Long> createdBookingIds = new ArrayList<>();

    @BeforeMethod
    public void setUp() {
        api    = new ApiClient();
        token  = api.login(Config.USER_EMAIL, Config.USER_PASSWORD);
        driver = DriverFactory.create();
        wait   = new WebDriverWait(driver, Config.WAIT);
        loginInBrowser(token);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        try {
            if (result.getStatus() == ITestResult.FAILURE && driver != null) {
                Screenshots.save(driver, result.getMethod().getMethodName());
            }
        } finally {
            if (driver != null) driver.quit();
            api.deleteBookings(token, createdBookingIds);
            createdBookingIds.clear();
        }
    }

    protected void loginInBrowser(String jwt) { /* see §7 */ }
}
```

### Standard Test
```java
public class BookingFlowTest extends BaseTest {

    @Test(description = "TC-001: bookings list shows a card with the booking details")
    public void tc001_bookingsListShowsCard() {
        // -- Step 1: Setup (create data via API) --
        Event event = api.findStaticEventWithSeats(token, 2);
        Booking booking = api.createBooking(token, event.id(), 2);
        createdBookingIds.add(booking.id());

        // -- Step 2: Action (drive the UI) --
        BookingsPage bookings = new BookingsPage(driver).open();

        // -- Step 3: Assert (verify outcomes) --
        WebElement card = bookings.cardFor(booking.bookingRef());
        Assert.assertTrue(card.getText().contains(event.title()));
    }
}
```
- Each `@Test` is self-contained (login → action → assert). Don't use `dependsOnMethods` or `priority` to chain tests
- Use `// -- Step N: Description --` comment blocks to separate logical steps
- Don't put locators or `driver.findElement` calls in the test when a page object exists. Tests call page methods and assert

### `testng.xml`
```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<suite name="EventHub Selenium" parallel="none">
  <test name="Booking">
    <classes>
      <class name="com.eventhub.tests.BookingFlowTest"/>
    </classes>
  </test>
</suite>
```

### Page Object Model (POM)

```java
// src/test/java/com/eventhub/pages/BookingsPage.java
public class BookingsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By heading     = headingText("My Bookings");
    private final By cards       = testId("booking-card");
    private final By clearAllBtn = buttonText("Clear all bookings");
    private final By emptyState  = headingText("No bookings yet");

    public BookingsPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Config.WAIT);
    }

    public BookingsPage open() {
        driver.get(Config.BASE_URL + "/bookings");
        wait.until(ExpectedConditions.visibilityOfElementLocated(heading));
        return this;
    }

    public WebElement cardFor(String bookingRef) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(Locators.bookingCardFor(bookingRef)));
    }

    public void clearAll() {
        driver.findElement(clearAllBtn).click();
        wait.until(ExpectedConditions.alertIsPresent()).accept();
    }

    public boolean isEmpty() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(emptyState)).isDisplayed();
    }
}
```

**POM Rules:**
- One page class per page/major component
- Store locators as `private final By` fields, **not** `WebElement`s. Elements go stale; locators don't
- Don't use `PageFactory` / `@FindBy`. Its lazy proxies hide staleness and timing problems
- Methods represent user actions (`clearAll()`, `cancelBooking(ref)`), not low-level steps
- Navigation methods return the next page object (`open()` returns `this`; `viewDetails(ref)` returns `BookingDetailPage`)
- Keep assertions in test classes, not in page objects. Return data (`String`, `boolean`, `WebElement`) for the test to assert on
- Page classes live in `com.eventhub.pages`

---

## 7. Auth Patterns

### Log in through the API, not the form
Only a login-specific test should drive the login form. Everything else gets a token from the API (see §8 for `ApiClient`):
```java
String token = api.login(Config.USER_EMAIL, Config.USER_PASSWORD);
```

### Inject the token into localStorage
The app reads the JWT from `localStorage.eventhub_token`. Selenium can only write localStorage for the origin that's currently loaded, so open the site first, set the token, then reload:
```java
protected void loginInBrowser(String jwt) {
    driver.get(Config.BASE_URL);
    ((JavascriptExecutor) driver).executeScript("localStorage.setItem('eventhub_token', arguments[0]);", jwt);
    driver.navigate().refresh();
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("nav-bookings")));
}
```
Always pass values as `arguments[0]`. Never concatenate them into the script string.

### Test Users
| User          | Email                                    | Password   | Purpose          |
|---------------|------------------------------------------|------------|------------------|
| Main user     | eventhub-selenium-main@qa-sandbox.dev    | Selenium1! | Primary tester   |
| Other user    | eventhub-selenium-other@qa-sandbox.dev   | Selenium1! | Cross-user tests |

These are Selenium's own users. Playwright uses `rahulshetty1@gmail.com` / `@yahoo.com`, so the two suites never share bookings, seat counts or the 9-booking limit, and can run in parallel. `BaseTest` registers the users in `@BeforeSuite` (`ApiClient.ensureAccount` logs in first and registers only if that fails; a duplicate register returns 400). Override them with `SELENIUM_USER_EMAIL`, `SELENIUM_USER_PASSWORD`, `SELENIUM_OTHER_USER_EMAIL` and `SELENIUM_OTHER_USER_PASSWORD`.

For cross-user tests, use a **separate `WebDriver`** for User B (create it in the test, quit it in a `finally`). Never share a browser session between users.

---

## 8. Test Data: Setup and Cleanup

### API client with the JDK `HttpClient`
```java
// src/test/java/com/eventhub/api/ApiClient.java
public class ApiClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public record Event(long id, String title, String venue, String city, double price, int availableSeats) {}
    public record Booking(long id, String bookingRef, int quantity, double totalPrice) {}

    public String login(String email, String password) {
        JsonNode body = send("POST", "/auth/login", null, Map.of("email", email, "password", password), 200);
        return body.get("token").asText();
    }

    public Event findStaticEventWithSeats(String token, int seats) {
        JsonNode events = send("GET", "/events?limit=50", token, null, 200).get("data");
        for (JsonNode e : events) {
            if (e.get("isStatic").asBoolean() && e.get("availableSeats").asInt() >= seats) {
                return new Event(e.get("id").asLong(), e.get("title").asText(), e.get("venue").asText(),
                        e.get("city").asText(), e.get("price").asDouble(), e.get("availableSeats").asInt());
            }
        }
        throw new IllegalStateException("No static event with " + seats + " free seats");
    }

    public Booking createBooking(String token, long eventId, int quantity) {
        Map<String, Object> payload = Map.of(
            "eventId", eventId, "quantity", quantity,
            "customerName", "Selenium QA", "customerEmail", "selenium.qa@example.com", "customerPhone", "9876543210");
        JsonNode b = send("POST", "/bookings", token, payload, 201).get("data");
        return new Booking(b.get("id").asLong(), b.get("bookingRef").asText(),
                b.get("quantity").asInt(), b.get("totalPrice").asDouble());
    }

    /** Best-effort cleanup: a 404 just means the test already removed it. */
    public void deleteBookings(String token, List<Long> ids) {
        for (Long id : ids) {
            try { send("DELETE", "/bookings/" + id, token, null, -1); } catch (RuntimeException ignored) {}
        }
    }

    private JsonNode send(String method, String path, String token, Object body, int expectedStatus) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(Config.API_URL + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json");
            if (token != null) req.header("Authorization", "Bearer " + token);
            req.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));

            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (expectedStatus > 0 && res.statusCode() != expectedStatus) {
                throw new AssertionError(method + " " + path + " -> " + res.statusCode() + ": " + res.body());
            }
            return JSON.readTree(res.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
```
- Pick a **static** event (`isStatic: true`) with enough seats. Don't hardcode titles or IDs; production seeds differ from the domain docs
- Use a recognizable `customerName` so leftovers from crashed runs can be found and deleted
- Restore the interrupt flag when catching `InterruptedException`. Never swallow it

### Clean up only what you created
- Add every booking ID to `createdBookingIds` **as soon as it exists**, including ones created through the UI (read the ID from the card's `#<id>` or from `GET /api/bookings`). Then `@AfterMethod` still cleans up if a later assertion fails
- Don't use "Clear all bookings" as cleanup. It wipes the whole shared account, including other tests' data

### Unique Test Data
```java
String eventTitle = "Selenium Event " + System.currentTimeMillis();
```

### Shared account rules
- The test accounts are public and shared. Bookings from other people or other runs can appear at any time
- Assert on **your own** data (filter by your booking ref) and on **relative** order, not "the first card is mine"
- Keep `parallel="none"`. A clear-all or FIFO test in one class will break another class running at the same time

---

## 9. Native Dialogs and Modals

### Native `confirm()` (e.g. "Clear all bookings")
```java
driver.findElement(buttonText("Clear all bookings")).click();
Alert alert = wait.until(ExpectedConditions.alertIsPresent());
Assert.assertEquals(alert.getText(), "Clear all your bookings? This cannot be undone.");
alert.accept();          // or alert.dismiss() to cancel
```
Any other WebDriver command throws `UnhandledAlertException` while the alert is open. Handle it right after the click.

### In-app modals (e.g. "Cancel this booking?")
These are normal DOM elements (`role="dialog"`), not alerts:
```java
WebElement dialog = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role='dialog']")));
Assert.assertTrue(dialog.getText().contains("Cancel this booking?"));
dialog.findElement(testId("confirm-dialog-yes")).click();
wait.until(ExpectedConditions.stalenessOf(dialog));
```

---

## 10. API Mocking

Selenium 4 can intercept network traffic through Chrome DevTools (`DevTools` / `NetworkInterceptor`), but it's Chrome-specific, verbose, and fragile across Chrome versions.

- **Don't** mock API responses in Selenium tests. Tests that need a specific UI state (empty list, error screen, loading skeleton, 403 page) belong in the Playwright component-style suite (`page.route()`), per `docs/test-strategy.md`
- Selenium tests should drive the **real** stack end to end. Create whatever state you need through the API (§8)

---

## 11. Debugging Tips

### Screenshot on failure
```java
// src/test/java/com/eventhub/utils/Screenshots.java
public final class Screenshots {
    private Screenshots() {}

    public static void save(WebDriver driver, String name) {
        try {
            Path dir = Path.of("target", "screenshots");
            Files.createDirectories(dir);
            File png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(png.toPath(), dir.resolve(name + ".png"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not save screenshot: " + e.getMessage());
        }
    }
}
```

### Browser console logs
Requires `goog:loggingPrefs` in the driver options (§1):
```java
driver.manage().logs().get(LogType.BROWSER)
      .forEach(entry -> System.out.println("[browser] " + entry.getLevel() + ": " + entry.getMessage()));
```

### Logging in tests
```java
System.out.printf("Created booking %s (#%d)%n", booking.bookingRef(), booking.id());
```
Use `Reporter.log(...)` instead when you want the line in the TestNG HTML report.

### Run a class / single test / headed
```bash
mvn -f selenium/pom.xml test
mvn -f selenium/pom.xml test -Dtest=BookingFlowTest
mvn -f selenium/pom.xml test -Dtest=BookingFlowTest#tc003_cancelFromDetailPage
mvn -f selenium/pom.xml test -Dtest=BookingFlowTest -Dheaded=true      # watch it in a real window
```
Reports: `selenium/target/surefire-reports/`. Open `test-case-report.html` for one row per test case (status, time, failure reason), written by `com.eventhub.utils.TestCaseReport` (registered in `testng.xml`). TestNG's own `index.html` hides the test list in a collapsed sidebar.

---

## 12. Anti-Patterns to Avoid

| Anti-Pattern | Why It's Bad | Do Instead |
|-------------|-------------|-----------|
| `Thread.sleep(N)` | Flaky, wastes time | `wait.until(ExpectedConditions...)` |
| `implicitlyWait(...)` | Mixed with explicit waits it gives unpredictable timeouts | Implicit wait 0; explicit waits only |
| `findElement` right after an action, no wait | Fails on React's async render | Wait for the element or condition first |
| Holding `WebElement`s across re-renders | `StaleElementReferenceException` | Store `By` locators; find again |
| `PageFactory` / `@FindBy` | Lazy proxies hide staleness and timing | `private final By` fields + explicit waits |
| `try/catch (NoSuchElementException)` to check absence | Slow and hides real failures | `numberOfElementsToBe(locator, 0)` |
| Swallowing `InterruptedException` | Breaks thread interruption | Restore the flag with `Thread.currentThread().interrupt()` |
| Absolute/positional XPath | Breaks on any layout change | `data-testid`, id, or content-filtered XPath |
| Tailwind classes as locators | Styling changes break tests; ambiguous matches | `data-testid` (ask developers to add one) |
| `driver.quit()` only on success | Leaks Chrome processes | `@AfterMethod(alwaysRun = true)` + `finally` |
| `assertEquals(expected, actual)` in TestNG | Swapped arguments give confusing failure messages | TestNG order is `(actual, expected, message)` |
| `SoftAssert` without `assertAll()` | Failures are silently dropped | Always end with `soft.assertAll()` |
| `dependsOnMethods` / `priority` to chain tests | Order-dependent failures, cascades of skips | Each `@Test` self-contained |
| Static `WebDriver` field | Breaks isolation; unsafe if parallel is ever enabled | Instance field (or `ThreadLocal<WebDriver>` if you go parallel) |
| Logging in through the form in every test | Slow, and tests the login page repeatedly | API login + localStorage token (§7) |
| Setup through the UI | Slow and brittle | Create data through the API (§8) |
| "Clear all bookings" as cleanup | Deletes other tests' data on the shared account | Delete only recorded IDs |
| Asserting "first card is mine" | Breaks when others book on the shared account | Filter by your ref; assert relative order |
| Hardcoded booking/event IDs or titles | Production data changes | Look them up through the API |
| Committed driver binaries / WebDriverManager | Version drift, extra dependency | Selenium Manager (built in) |
| Selenium code under `tests/` | Mixes with the Playwright suite | Separate `selenium/` Maven module |
| No assertions after an action | Test passes but proves nothing | Always assert outcomes |
