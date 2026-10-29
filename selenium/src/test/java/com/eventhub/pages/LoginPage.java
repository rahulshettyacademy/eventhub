package com.eventhub.pages;

import com.eventhub.config.Config;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static com.eventhub.utils.Locators.headingText;

/** /login — the sign-in form. Only login-related tests use it; others log in through the API. */
public class LoginPage {
    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By heading  = headingText("Sign in to EventHub");
    private final By email    = By.id("email");
    private final By password = By.id("password");
    private final By loginBtn = By.id("login-btn");

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Config.WAIT);
    }

    /** We're on /login and the form is ready (also use after a redirect here). */
    public LoginPage waitForLoaded() {
        wait.until(ExpectedConditions.urlToBe(Config.BASE_URL + "/login"));
        wait.until(ExpectedConditions.visibilityOfElementLocated(heading));
        wait.until(ExpectedConditions.elementToBeClickable(loginBtn));
        return this;
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    /** Signs in through the form and waits for the app to land on the home page. */
    public void loginAs(String userEmail, String userPassword) {
        driver.findElement(email).sendKeys(userEmail);
        driver.findElement(password).sendKeys(userPassword);
        driver.findElement(loginBtn).click();
        wait.until(ExpectedConditions.urlToBe(Config.BASE_URL + "/"));
    }
}
