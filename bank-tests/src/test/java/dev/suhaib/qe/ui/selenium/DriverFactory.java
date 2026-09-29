package dev.suhaib.qe.ui.selenium;

import dev.suhaib.qe.config.TestConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

/** Creates a local WebDriver. Selenium Manager resolves the matching driver binary automatically. */
public final class DriverFactory {

    private DriverFactory() {
    }

    public static WebDriver create() {
        return switch (TestConfig.seleniumBrowser().toLowerCase()) {
            case "firefox" -> firefox();
            case "chrome" -> chrome();
            default -> throw new IllegalArgumentException("Unsupported selenium.browser: " + TestConfig.seleniumBrowser());
        };
    }

    private static WebDriver chrome() {
        ChromeOptions options = new ChromeOptions();
        if (TestConfig.headless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1280,900", "--no-sandbox", "--disable-dev-shm-usage");
        TestConfig.chromeBinary().ifPresent(options::setBinary);
        return new ChromeDriver(options);
    }

    private static WebDriver firefox() {
        FirefoxOptions options = new FirefoxOptions();
        if (TestConfig.headless()) {
            options.addArguments("-headless");
        }
        return new FirefoxDriver(options);
    }
}
