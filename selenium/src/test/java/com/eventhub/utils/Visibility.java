package com.eventhub.utils;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Visibility checks that return a real true/false instead of throwing, so page
 * objects can hand a boolean to the test and the test's assertion message
 * (or SoftAssert) reports the failure.
 */
public final class Visibility {
    private Visibility() {}

    /** Is a matching element displayed right now? Use after the page or parent has loaded. */
    public static boolean isDisplayed(SearchContext context, By locator) {
        return context.findElements(locator).stream().anyMatch(WebElement::isDisplayed);
    }

    /** Does a matching element become visible within the wait's timeout? */
    public static boolean appears(WebDriverWait wait, By locator) {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }
}
