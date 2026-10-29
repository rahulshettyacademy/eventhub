package com.eventhub.base;

import com.eventhub.api.ApiClient;
import com.eventhub.config.Config;
import com.eventhub.utils.Screenshots;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.util.ArrayList;
import java.util.List;

import static com.eventhub.utils.Locators.testId;

/** Per-test setup: API login, a fresh logged-in browser, and cleanup of recorded bookings. */
public abstract class BaseTest {
    protected WebDriver driver;
    protected WebDriverWait wait;
    protected ApiClient api;
    protected String token;
    protected final List<Long> createdBookingIds = new ArrayList<>();

    /** Once per run: make sure Selenium's own accounts exist (a fresh clone or a reset site has none). */
    @BeforeSuite(alwaysRun = true)
    public void ensureAccounts() {
        ApiClient setup = new ApiClient();
        setup.ensureAccount(Config.USER_EMAIL, Config.USER_PASSWORD);
        setup.ensureAccount(Config.OTHER_USER_EMAIL, Config.OTHER_USER_PASSWORD);
    }

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
            // Nested so booking cleanup still runs if quit() throws (e.g. Chrome crashed)
            try {
                if (driver != null) driver.quit();
            } finally {
                if (api != null) api.deleteBookings(token, createdBookingIds);
                createdBookingIds.clear();
            }
        }
    }

    /** The app reads the JWT from localStorage; it can only be set once the origin is loaded. */
    protected void loginInBrowser(String jwt) {
        loginInBrowser(driver, jwt);
    }

    /** Same, for a second browser (e.g. another user in a cross-user test). */
    protected static void loginInBrowser(WebDriver browser, String jwt) {
        browser.get(Config.BASE_URL);
        ((JavascriptExecutor) browser).executeScript("localStorage.setItem('eventhub_token', arguments[0]);", jwt);
        browser.navigate().refresh();
        new WebDriverWait(browser, Config.WAIT)
                .until(ExpectedConditions.visibilityOfElementLocated(testId("nav-bookings")));
    }
}
