package dev.suhaib.qe.config;

import java.util.Optional;

/**
 * Single source of truth for run settings. Each value comes from a JVM system property
 * (-Dbase.url=...) or the matching environment variable (BASE_URL=...), in that order.
 */
public final class TestConfig {

    private TestConfig() {
    }

    /** Where the bank is running. Unset means: boot the app in-process (see {@code AppLauncher}). */
    public static Optional<String> baseUrl() {
        return value("base.url");
    }

    public static String requireBaseUrl() {
        return baseUrl().orElseThrow(() -> new IllegalStateException("base.url is not set and the app was not started"));
    }

    /** JDBC URL of the bank's database, used by the SQL data checks. */
    public static Optional<String> dbUrl() {
        return value("db.url");
    }

    public static String dbUser() {
        return value("db.user").orElse("sa");
    }

    public static String dbPassword() {
        return value("db.password").orElse("");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(value("headless").orElse("true"));
    }

    /** Selenium browser: chrome (default) or firefox. */
    public static String seleniumBrowser() {
        return value("selenium.browser").orElse("chrome");
    }

    /** Optional browser binary override, handy when running against a preinstalled Chromium. */
    public static Optional<String> chromeBinary() {
        return value("chrome.binary");
    }

    public static int uiTimeoutMillis() {
        return Integer.parseInt(value("ui.timeout.ms").orElse("10000"));
    }

    public static Optional<String> value(String key) {
        String fromProperty = System.getProperty(key);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Optional.of(fromProperty);
        }
        String fromEnv = System.getenv(key.toUpperCase().replace('.', '_'));
        return Optional.ofNullable(fromEnv).filter(v -> !v.isBlank());
    }
}
