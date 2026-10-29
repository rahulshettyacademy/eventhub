package com.eventhub.base;

import com.eventhub.config.Config;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.Map;

public final class DriverFactory {
    private DriverFactory() {}

    public static WebDriver create() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1366,900");
        if (!Config.HEADED) options.addArguments("--headless=new");
        if (Config.NO_SANDBOX) options.addArguments("--no-sandbox");
        options.setCapability("goog:loggingPrefs", Map.of("browser", "ALL"));

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        // Implicit wait stays at 0 (the default): explicit waits only
        return driver;
    }
}
