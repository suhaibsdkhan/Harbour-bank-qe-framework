package dev.suhaib.qe.ui.playwright;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import dev.suhaib.qe.api.BankApi;
import dev.suhaib.qe.data.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("ui")
@Tag("playwright")
@Epic("Online banking UI")
@Feature("Playwright")
@ExtendWith(PlaywrightExtension.class)
class PlaywrightBankingTest {

    private final BankApi api = new BankApi();

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Customer opens an account and sees it listed with the opening balance")
    void open_account(Page page) {
        BankHomePage home = new BankHomePage(page).open();
        String owner = TestData.ownerName();

        String accountNumber = home.openAccount(owner, "SAVINGS", "125.50");

        assertThat(home.accountRow(accountNumber)).containsText(owner);
        assertThat(home.balanceOf(accountNumber)).hasText("$125.50");
        assertThat(home.accountRow(accountNumber).getByTestId("account-status")).hasText("ACTIVE");
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Customer transfers money and both balances and the history update")
    void transfer_between_accounts(Page page) {
        // Arrange through the API (fast), act and assert through the UI (what the customer sees).
        String from = api.openFundedAccount("800.00");
        String to = api.openFundedAccount("50.00");
        BankHomePage home = new BankHomePage(page).open();

        home.transfer(from, to, "300.25", "Tuition");

        assertThat(home.alert()).containsText("completed");
        assertThat(home.balanceOf(from)).hasText("$499.75");
        assertThat(home.balanceOf(to)).hasText("$350.25");
        assertThat(home.detailsBalance()).hasText("$499.75");
        assertThat(home.transactionRows().first()).containsText("Tuition");
        assertThat(home.transactionRows().first().getByTestId("transaction-type")).hasText("DEBIT");
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("An overdraft attempt shows the INSUFFICIENT_FUNDS error and leaves balances untouched")
    void insufficient_funds_message(Page page) {
        String from = api.openFundedAccount("20.00");
        String to = api.openFundedAccount("0.00");
        BankHomePage home = new BankHomePage(page).open();

        home.transfer(from, to, "20.01", "Too much");

        assertThat(home.alert()).hasAttribute("data-kind", "error");
        assertThat(home.alert()).containsText("INSUFFICIENT_FUNDS");
        assertThat(home.balanceOf(from)).hasText("$20.00");
    }

    @Test
    @DisplayName("Submitting the account form without a name shows a validation error")
    void blank_owner_validation(Page page) {
        BankHomePage home = new BankHomePage(page).open();

        page.getByTestId("open-account-submit").click();

        assertThat(home.alert()).hasAttribute("data-kind", "error");
        assertThat(home.alert()).containsText("ownerName");
    }

    @Test
    @DisplayName("A deposit made in the UI is visible through the API")
    void ui_deposit_visible_in_api(Page page) {
        String account = api.openFundedAccount("10.00");
        BankHomePage home = new BankHomePage(page).open();

        home.deposit(account, "15.55");

        assertThat(home.alert()).containsText("New balance $25.55");
        org.assertj.core.api.Assertions.assertThat(api.balanceOf(account)).isEqualByComparingTo("25.55");
    }
}
