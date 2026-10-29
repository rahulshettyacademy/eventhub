package com.eventhub.pages;

import com.eventhub.config.Config;
import com.eventhub.utils.Locators;
import com.eventhub.utils.Visibility;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

import static com.eventhub.utils.Locators.headingText;
import static com.eventhub.utils.Locators.testId;

/** /bookings — the "My Bookings" list. */
public class BookingsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By navBookings = testId("nav-bookings");
    private final By heading     = headingText("My Bookings");
    private final By cards       = testId("booking-card");
    private final By emptyState  = headingText("No bookings yet");
    private final By viewDetails = By.linkText("View Details");
    private final By cancelBtn   = testId("cancel-booking-btn");
    private final By bookingId   = testId("booking-id");
    // data-testid="booking-ref" is in the frontend source but not deployed to the live site yet
    private final By bookingRef  = By.cssSelector(".booking-ref");
    private final By dialog      = By.cssSelector("[role='dialog']");
    private final By confirmYes  = testId("confirm-dialog-yes");

    public BookingsPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Config.WAIT);
    }

    public BookingsPage open() {
        driver.get(Config.BASE_URL + "/bookings");
        return waitForLoaded();
    }

    /** Opens the list the way a user does: from the navbar on the home page. */
    public BookingsPage openFromNavbar() {
        driver.get(Config.BASE_URL);
        wait.until(ExpectedConditions.elementToBeClickable(navBookings)).click();
        return waitForLoaded();
    }

    /**
     * We're on /bookings and the list has finished loading: it shows cards or
     * the empty state (not the skeletons). Also use this after an action that
     * redirects here, such as cancelling from the detail page.
     */
    public BookingsPage waitForLoaded() {
        wait.until(ExpectedConditions.urlToBe(Config.BASE_URL + "/bookings"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(heading));
        wait.until(d -> !d.findElements(cards).isEmpty() || !d.findElements(emptyState).isEmpty());
        return this;
    }

    public boolean isHeadingVisible() {
        return Visibility.isDisplayed(driver, heading);
    }

    public WebElement cardFor(String bookingRef) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(Locators.bookingCardFor(bookingRef)));
    }

    public String cardText(String bookingRef) {
        return cardFor(bookingRef).getText();
    }

    public String cardBookingId(String bookingRef) {
        return cardFor(bookingRef).findElement(bookingId).getText();
    }

    public boolean cardHasViewDetails(String bookingRef) {
        return Visibility.isDisplayed(cardFor(bookingRef), viewDetails);
    }

    public boolean cardHasCancelButton(String bookingRef) {
        return Visibility.isDisplayed(cardFor(bookingRef), cancelBtn);
    }

    public BookingDetailPage viewDetails(String bookingRef) {
        cardFor(bookingRef).findElement(viewDetails).click();
        return new BookingDetailPage(driver).waitForLoaded();
    }

    /** Booking refs of the listed cards, top to bottom (the list is ordered newest first). */
    public List<String> refsInOrder() {
        return driver.findElements(cards).stream()
                .map(card -> card.findElement(bookingRef).getText().trim())
                .toList();
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    /** Clicks "Cancel Booking" on this ref's card and returns the confirmation dialog's text. */
    public String openCancelDialog(String bookingRef) {
        cardFor(bookingRef).findElement(cancelBtn).click();
        return wait.until(ExpectedConditions.visibilityOfElementLocated(dialog)).getText();
    }

    /**
     * Confirms the open dialog. Returns right after the click so the caller can
     * check the toast before it auto-dismisses.
     */
    public void confirmCancel() {
        driver.findElement(dialog).findElement(confirmYes).click();
    }

    /** Waits for the confirmation dialog to close; returns false if it's still open. */
    public boolean waitForDialogClosed() {
        try {
            wait.until(ExpectedConditions.numberOfElementsToBe(dialog, 0));
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }

    /** Waits until no card for this ref is shown; returns false if it's still there after the wait. */
    public boolean waitForCardGone(String bookingRef) {
        try {
            wait.until(ExpectedConditions.numberOfElementsToBe(Locators.bookingCardFor(bookingRef), 0));
            return true;
        } catch (org.openqa.selenium.TimeoutException e) {
            return false;
        }
    }
}
