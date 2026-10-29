package com.eventhub.utils;

import org.openqa.selenium.By;

/** Shared locator helpers, in the priority order from selenium-best-practices §2. */
public final class Locators {
    private Locators() {}

    public static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    public static By buttonText(String text) {
        return By.xpath("//button[normalize-space()='" + text + "']");
    }

    public static By headingText(String text) {
        return By.xpath("//*[self::h1 or self::h2 or self::h3][normalize-space()='" + text + "']");
    }

    public static By exactText(String text) {
        return By.xpath("//*[normalize-space()='" + text + "']");
    }

    /** The booking card whose ref badge shows exactly this booking ref. */
    public static By bookingCardFor(String bookingRef) {
        return By.xpath("//*[@data-testid='booking-card']"
                + "[.//*[contains(@class,'booking-ref') and normalize-space()='" + bookingRef + "']]");
    }
}
