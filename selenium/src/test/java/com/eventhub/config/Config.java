package com.eventhub.config;

import java.time.Duration;

/** Test settings. Override URLs and headed mode with -DbaseUrl, -DapiUrl, -Dheaded=true. */
public final class Config {
    private Config() {}

    public static final String BASE_URL = System.getProperty("baseUrl", "https://eventhub.rahulshettyacademy.com");
    public static final String API_URL  = System.getProperty("apiUrl",  "https://api.eventhub.rahulshettyacademy.com/api");
    public static final boolean HEADED  = Boolean.getBoolean("headed");
    /** Set by the Docker image, where Chrome runs as root and needs --no-sandbox. */
    public static final boolean NO_SANDBOX = Boolean.parseBoolean(System.getenv("CHROME_NO_SANDBOX"));
    public static final Duration WAIT   = Duration.ofSeconds(5);

    // Selenium has its own accounts, separate from Playwright's (rahulshetty1@gmail.com / @yahoo.com).
    // Bookings, seat counts and the 9-booking limit are per user, so the two suites can run in parallel.
    // BaseTest registers these accounts if they don't exist yet. Override with SELENIUM_* env vars.
    public static final String USER_EMAIL    = env("SELENIUM_USER_EMAIL", "eventhub-selenium-main@qa-sandbox.dev");
    public static final String USER_PASSWORD = env("SELENIUM_USER_PASSWORD", "Selenium1!");

    /** Second account, for cross-user (sandbox isolation) tests. */
    public static final String OTHER_USER_EMAIL    = env("SELENIUM_OTHER_USER_EMAIL", "eventhub-selenium-other@qa-sandbox.dev");
    public static final String OTHER_USER_PASSWORD = env("SELENIUM_OTHER_USER_PASSWORD", "Selenium1!");

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
