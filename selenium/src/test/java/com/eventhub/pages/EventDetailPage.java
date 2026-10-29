package com.eventhub.pages;

import com.eventhub.config.Config;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.eventhub.utils.Locators.buttonText;
import static com.eventhub.utils.Locators.headingText;
import static com.eventhub.utils.Locators.testId;

/** /events/:id — event details, the booking form and, after booking, the confirmation card. */
public class EventDetailPage {
    private static final Pattern SEATS = Pattern.compile("(\\d+) / (\\d+) seats");

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By title          = By.tagName("h1");
    private final By availableSeats = By.xpath("//*[normalize-space()='Available']/following-sibling::*[1]");
    private final By ticketCount    = By.id("ticket-count");
    private final By plusBtn        = buttonText("+");
    private final By priceLine      = By.xpath("//form[.//*[@id='ticket-count']]//span[contains(normalize-space(),'×')]");
    private final By formTotal      = By.xpath("//form[.//*[@id='ticket-count']]//span[normalize-space()='Total']/following-sibling::span");
    private final By fullName       = By.id("customerName");
    private final By email          = By.id("customer-email");
    private final By phone          = By.id("phone");
    private final By confirmBtn     = By.id("confirm-booking");
    private final By confirmedTitle = headingText("Booking Confirmed! 🎉");
    // data-testid="booking-ref" is in the frontend source but not deployed to the live site yet
    private final By bookingRef     = By.cssSelector(".booking-ref");
    private final By viewMyBookings = buttonText("View My Bookings");

    public EventDetailPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Config.WAIT);
    }

    public EventDetailPage open(long eventId) {
        driver.get(Config.BASE_URL + "/events/" + eventId);
        wait.until(ExpectedConditions.visibilityOfElementLocated(ticketCount));
        return this;
    }

    public String title() {
        return driver.findElement(title).getText();
    }

    /** The "Available" meta item, e.g. "2653 / 10000 seats" -> 2653. */
    public int availableSeats() {
        String text = wait.until(ExpectedConditions.visibilityOfElementLocated(availableSeats)).getText();
        Matcher m = SEATS.matcher(text);
        if (!m.find()) throw new IllegalStateException("Unexpected seats text: " + text);
        return Integer.parseInt(m.group(1));
    }

    public int ticketCount() {
        return Integer.parseInt(driver.findElement(ticketCount).getText().trim());
    }

    /** Clicks "+" until the ticket counter shows the wanted quantity. */
    public EventDetailPage selectTickets(int quantity) {
        while (ticketCount() < quantity) {
            int before = ticketCount();
            wait.until(ExpectedConditions.elementToBeClickable(plusBtn)).click();
            wait.until(ExpectedConditions.textToBe(ticketCount, String.valueOf(before + 1)));
        }
        return this;
    }

    /** The price line under the form, e.g. "$149 × 3 tickets". */
    public String priceLine() {
        return driver.findElement(priceLine).getText();
    }

    /** The form's "Total" value, e.g. "$447". */
    public String formTotal() {
        return driver.findElement(formTotal).getText();
    }

    public EventDetailPage fillCustomer(String name, String customerEmail, String customerPhone) {
        driver.findElement(fullName).sendKeys(name);
        driver.findElement(email).sendKeys(customerEmail);
        driver.findElement(phone).sendKeys(customerPhone);
        return this;
    }

    /** Submits the form and waits for the confirmation card. */
    public EventDetailPage confirmBooking() {
        wait.until(ExpectedConditions.elementToBeClickable(confirmBtn)).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(confirmedTitle));
        return this;
    }

    public String confirmedBookingRef() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(bookingRef)).getText().trim();
    }

    /** Text of the confirmation card (ref, customer, tickets, total). */
    public String confirmationText() {
        WebElement card = driver.findElement(By.xpath("//h3[normalize-space()='Booking Confirmed! 🎉']/.."));
        return card.getText();
    }

    public BookingsPage viewMyBookings() {
        wait.until(ExpectedConditions.elementToBeClickable(viewMyBookings)).click();
        return new BookingsPage(driver).waitForLoaded();
    }
}
