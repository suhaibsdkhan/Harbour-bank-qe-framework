package dev.suhaib.qe.ui.selenium;

import dev.suhaib.qe.config.TestConfig;
import io.qameta.allure.Step;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Selenium page object for the same page, using explicit waits (no implicit waits, no sleeps). */
public class HomePage {

    private static final By ALERT = testId("alert");
    private static final By ACCOUNTS_TABLE = testId("accounts-table");
    private static final By REFRESH = testId("refresh-accounts");
    private static final By DEPOSIT_ACCOUNT = testId("deposit-account");
    private static final By DEPOSIT_AMOUNT = testId("deposit-amount");
    private static final By DEPOSIT_SUBMIT = testId("deposit-submit");
    private static final By TRANSFER_FROM = testId("transfer-from");
    private static final By TRANSFER_TO = testId("transfer-to");
    private static final By TRANSFER_AMOUNT = testId("transfer-amount");
    private static final By TRANSFER_MEMO = testId("transfer-description");
    private static final By TRANSFER_SUBMIT = testId("transfer-submit");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public HomePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofMillis(TestConfig.uiTimeoutMillis()));
    }

    static By testId(String id) {
        return By.cssSelector("[data-testid='" + id + "']");
    }

    @Step("Open the online banking page")
    public HomePage open() {
        driver.get(TestConfig.requireBaseUrl() + "/");
        wait.until(ExpectedConditions.visibilityOfElementLocated(ACCOUNTS_TABLE));
        return this;
    }

    @Step("Deposit {amount} into {accountNumber}")
    public HomePage deposit(String accountNumber, String amount) {
        selectAccount(DEPOSIT_ACCOUNT, accountNumber);
        type(DEPOSIT_AMOUNT, amount);
        driver.findElement(DEPOSIT_SUBMIT).click();
        return this;
    }

    @Step("Transfer {amount} from {from} to {to}")
    public HomePage transfer(String from, String to, String amount, String memo) {
        selectAccount(TRANSFER_FROM, from);
        selectAccount(TRANSFER_TO, to);
        type(TRANSFER_AMOUNT, amount);
        type(TRANSFER_MEMO, memo);
        driver.findElement(TRANSFER_SUBMIT).click();
        return this;
    }

    /** Waits for the alert to show {@code expected} and returns the alert text. */
    public String waitForAlertContaining(String expected) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(ALERT, expected));
        return driver.findElement(ALERT).getText();
    }

    public String alertKind() {
        return driver.findElement(ALERT).getAttribute("data-kind");
    }

    public String balanceOf(String accountNumber) {
        By balance = By.cssSelector("[data-testid='account-row-" + accountNumber + "'] [data-testid='account-balance']");
        return wait.until(ExpectedConditions.visibilityOfElementLocated(balance)).getText();
    }

    /** Waits until the account's balance cell shows {@code expected}; the table re-renders asynchronously. */
    public void waitForBalance(String accountNumber, String expected) {
        By balance = By.cssSelector("[data-testid='account-row-" + accountNumber + "'] [data-testid='account-balance']");
        wait.until(ExpectedConditions.textToBe(balance, expected));
    }

    public String statusOf(String accountNumber) {
        By status = By.cssSelector("[data-testid='account-row-" + accountNumber + "'] [data-testid='account-status']");
        return wait.until(ExpectedConditions.visibilityOfElementLocated(status)).getText();
    }

    private void selectAccount(By locator, String accountNumber) {
        // Accounts created through the API after page load need a refresh before they are selectable.
        wait.until(d -> {
            if (d.findElements(By.cssSelector("[data-testid='account-row-" + accountNumber + "']")).isEmpty()) {
                d.findElement(REFRESH).click();
                return false;
            }
            return true;
        });
        new Select(driver.findElement(locator)).selectByValue(accountNumber);
    }

    private void type(By locator, String text) {
        WebElement input = driver.findElement(locator);
        input.clear();
        input.sendKeys(text);
    }
}
