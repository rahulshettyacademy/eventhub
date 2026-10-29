package com.eventhub.pages;

import com.eventhub.config.Config;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import static com.eventhub.utils.Locators.testId;

/** The top navigation bar shown on every logged-in page. */
public class Navbar {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By logoutBtn   = testId("logout-btn");
    private final By userEmail   = testId("user-email-display");

    public Navbar(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Config.WAIT);
    }

    /** The navbar renders desktop and mobile copies of some controls; use whichever is visible. */
    private WebElement visible(By locator) {
        return wait.until(d -> d.findElements(locator).stream()
                .filter(WebElement::isDisplayed).findFirst().orElse(null));
    }

    public String loggedInEmail() {
        return visible(userEmail).getText().trim();
    }

    /** Logs out; the app sends the user to /login. */
    public LoginPage logout() {
        visible(logoutBtn).click();
        return new LoginPage(driver).waitForLoaded();
    }
}
