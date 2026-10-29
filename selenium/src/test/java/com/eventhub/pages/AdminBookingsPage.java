package com.eventhub.pages;

import com.eventhub.config.Config;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eventhub.utils.Locators.headingText;
import static com.eventhub.utils.Locators.testId;

/** /admin/bookings — "Manage Bookings": a table of this user's bookings, a detail modal and cancel. */
public class AdminBookingsPage {
    private static final Pattern TOTAL = Pattern.compile("(\\d+) total bookings");

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By heading     = headingText("Manage Bookings");
    private final By subtitle    = By.xpath("//h1[normalize-space()='Manage Bookings']/following-sibling::p");
    private final By headerCells = By.cssSelector("table thead th");
    private final By rows        = By.cssSelector("table tbody tr");
    private final By dialog      = By.cssSelector("[role='dialog']");
    private final By modalTitle  = By.id("modal-title");
    private final By modalClose  = By.cssSelector("[role='dialog'] button[aria-label='Close']");
    private final By confirmYes  = testId("confirm-dialog-yes");

    public AdminBookingsPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Config.WAIT);
    }

    /** Opens the page and waits until the table and the "<N> total bookings" subtitle are rendered. */
    public AdminBookingsPage open() {
        driver.get(Config.BASE_URL + "/admin/bookings");
        wait.until(ExpectedConditions.visibilityOfElementLocated(heading));
        wait.until(ExpectedConditions.textMatches(subtitle, TOTAL));
        wait.until(ExpectedConditions.visibilityOfElementLocated(rows));
        return this;
    }

    /** The subtitle's count, e.g. "4 total bookings" -> 4. Waits for it to settle on a number first. */
    public int totalBookings() {
        String text = wait.until(ExpectedConditions.visibilityOfElementLocated(subtitle)).getText();
        Matcher m = TOTAL.matcher(text);
        if (!m.find()) throw new IllegalStateException("Unexpected subtitle: " + text);
        return Integer.parseInt(m.group(1));
    }

    /** Waits until the subtitle shows this count; returns false if it doesn't within the wait. */
    public boolean waitForTotal(int expected) {
        try {
            wait.until(ExpectedConditions.textToBe(subtitle, expected + " total bookings"));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public List<String> columnHeaders() {
        return driver.findElements(headerCells).stream().map(th -> th.getText().trim()).toList();
    }

    /** The table row whose Ref cell shows exactly this booking ref. */
    private By rowFor(String bookingRef) {
        return By.xpath("//table/tbody/tr[td[1]//span[normalize-space()='" + bookingRef + "']]");
    }

    public WebElement row(String bookingRef) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(rowFor(bookingRef)));
    }

    /** The row's cells, in column order (Ref, Customer, Event, Qty, Total, Status, Date, Actions). */
    public List<String> rowCells(String bookingRef) {
        return row(bookingRef).findElements(By.tagName("td")).stream().map(td -> td.getText().trim()).toList();
    }

    /** Clicks "View" in the row; returns the detail modal's title, e.g. "Booking — D-ABC123". */
    public String openDetails(String bookingRef) {
        row(bookingRef).findElement(By.xpath(".//button[normalize-space()='View']")).click();
        return wait.until(ExpectedConditions.visibilityOfElementLocated(modalTitle)).getText();
    }

    public String modalText() {
        return driver.findElement(dialog).getText();
    }

    /** Closes the detail modal with its X button and waits for it to go away. */
    public void closeModal() {
        driver.findElement(modalClose).click();
        wait.until(ExpectedConditions.numberOfElementsToBe(dialog, 0));
    }

    /** Clicks "Cancel" in the row; returns the confirmation dialog's text. */
    public String openCancelDialog(String bookingRef) {
        row(bookingRef).findElement(By.xpath(".//button[normalize-space()='Cancel']")).click();
        return wait.until(ExpectedConditions.visibilityOfElementLocated(dialog)).getText();
    }

    /** Clicks "Yes, cancel it". Returns right away so the caller can check the toast before it auto-dismisses. */
    public void confirmCancel() {
        driver.findElement(dialog).findElement(confirmYes).click();
    }

    /** Waits until no row for this ref is shown; returns false if it's still there after the wait. */
    public boolean waitForRowGone(String bookingRef) {
        try {
            wait.until(ExpectedConditions.numberOfElementsToBe(rowFor(bookingRef), 0));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isRowShown(String bookingRef) {
        return !driver.findElements(rowFor(bookingRef)).isEmpty();
    }
}
