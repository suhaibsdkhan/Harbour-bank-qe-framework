package dev.suhaib.qe.ui.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Step;
import java.util.regex.Pattern;

/** Page object for the online banking home page, built on Playwright's auto-waiting locators. */
public class BankHomePage {

    private static final Pattern ACCOUNT_OPENED = Pattern.compile("Account (\\d{10}) opened");

    private final Page page;

    public BankHomePage(Page page) {
        this.page = page;
    }

    private Locator byTestId(String id) {
        return page.getByTestId(id);
    }

    @Step("Open the online banking page")
    public BankHomePage open() {
        page.navigate("/");
        assertThat(byTestId("accounts-table")).isVisible();
        return this;
    }

    @Step("Open a {type} account for {owner} with {initialDeposit}")
    public String openAccount(String owner, String type, String initialDeposit) {
        byTestId("owner-name").fill(owner);
        byTestId("account-type").selectOption(type);
        byTestId("initial-deposit").fill(initialDeposit);
        byTestId("open-account-submit").click();
        assertThat(alert()).hasAttribute("data-kind", "success");
        var matcher = ACCOUNT_OPENED.matcher(alert().textContent());
        if (!matcher.find()) {
            throw new AssertionError("Unexpected alert: " + alert().textContent());
        }
        String accountNumber = matcher.group(1);
        assertThat(accountRow(accountNumber)).isVisible();
        return accountNumber;
    }

    @Step("Deposit {amount} into {accountNumber}")
    public BankHomePage deposit(String accountNumber, String amount) {
        byTestId("deposit-account").selectOption(accountNumber);
        byTestId("deposit-amount").fill(amount);
        byTestId("deposit-submit").click();
        return this;
    }

    @Step("Transfer {amount} from {from} to {to}")
    public BankHomePage transfer(String from, String to, String amount, String memo) {
        byTestId("transfer-from").selectOption(from);
        byTestId("transfer-to").selectOption(to);
        byTestId("transfer-amount").fill(amount);
        byTestId("transfer-description").fill(memo);
        byTestId("transfer-submit").click();
        return this;
    }

    @Step("View account {accountNumber}")
    public BankHomePage viewAccount(String accountNumber) {
        accountRow(accountNumber).getByTestId("account-link").click();
        assertThat(byTestId("details-number")).hasText(accountNumber);
        return this;
    }

    public Locator alert() {
        return byTestId("alert");
    }

    public Locator accountRow(String accountNumber) {
        return byTestId("account-row-" + accountNumber);
    }

    public Locator balanceOf(String accountNumber) {
        return accountRow(accountNumber).getByTestId("account-balance");
    }

    public Locator detailsBalance() {
        return byTestId("details-balance");
    }

    public Locator transactionRows() {
        return byTestId("transaction-row");
    }
}
