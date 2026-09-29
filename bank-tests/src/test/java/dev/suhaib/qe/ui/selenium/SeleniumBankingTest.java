package dev.suhaib.qe.ui.selenium;

import static org.assertj.core.api.Assertions.assertThat;

import dev.suhaib.qe.api.BankApi;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.WebDriver;

@Tag("ui")
@Tag("selenium")
@Epic("Online banking UI")
@Feature("Selenium WebDriver")
@ExtendWith(SeleniumExtension.class)
class SeleniumBankingTest {

    private final BankApi api = new BankApi();

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Customer makes a deposit and sees the new balance")
    void deposit(WebDriver driver) {
        String account = api.openFundedAccount("40.00");
        HomePage home = new HomePage(driver).open();

        home.deposit(account, "60.00");

        assertThat(home.waitForAlertContaining("Deposited")).contains("New balance $100.00");
        home.waitForBalance(account, "$100.00");
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Customer transfers money between accounts")
    void transfer(WebDriver driver) {
        String from = api.openFundedAccount("1000.00");
        String to = api.openFundedAccount("0.00");
        HomePage home = new HomePage(driver).open();

        home.transfer(from, to, "999.99", "Car payment");

        assertThat(home.waitForAlertContaining("completed")).contains("$999.99 sent to " + to);
        home.waitForBalance(from, "$0.01");
        home.waitForBalance(to, "$999.99");
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("A frozen account is flagged and cannot send money")
    void frozen_account(WebDriver driver) {
        String from = api.openFundedAccount("100.00");
        String to = api.openFundedAccount("0.00");
        api.freeze(from).then().statusCode(200);
        HomePage home = new HomePage(driver).open();

        assertThat(home.statusOf(from)).isEqualTo("FROZEN");
        home.transfer(from, to, "10.00", "Blocked");

        assertThat(home.waitForAlertContaining("ACCOUNT_FROZEN")).contains("frozen");
        assertThat(home.alertKind()).isEqualTo("error");
        assertThat(home.balanceOf(from)).isEqualTo("$100.00");
    }
}
