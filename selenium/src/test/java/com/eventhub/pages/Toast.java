package com.eventhub.pages;

import com.eventhub.config.Config;
import com.eventhub.utils.Visibility;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import static com.eventhub.utils.Locators.exactText;

/**
 * App-wide toast notifications. Toasts survive page navigation but remove
 * themselves after 4.5s (Toast.tsx), so check them right after the action
 * that triggers them, before waiting on anything else.
 */
public class Toast {
    private final WebDriverWait wait;

    public Toast(WebDriver driver) {
        this.wait = new WebDriverWait(driver, Config.WAIT);
    }

    public boolean isShown(String message) {
        return Visibility.appears(wait, exactText(message));
    }
}
