package dev.suhaib.qe.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.suhaib.qe.api.BankApi;
import dev.suhaib.qe.data.TestData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Whole-database invariants a bank must never break. They run against every row in the database,
 * so they also catch damage done by any other test in the run.
 */
@Tag("db")
@Epic("Data integrity")
@Feature("Ledger")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataIntegrityTest {

    private final BankApi api = new BankApi();
    private final BankDb db = new BankDb();

    private String payer;
    private String payee;
    private String completedRef;
    private String rejectedRef;

    @BeforeAll
    void createMixedActivity() {
        assumeTrue(BankDb.isConfigured(), "db.url is not set; skipping SQL checks");
        payer = api.openFundedAccount("1000.00");
        payee = api.openFundedAccount("0.00");
        completedRef = api.transfer(payer, payee, TestData.cad("250.00")).then().statusCode(201).extract().path("reference");
        api.deposit(payee, TestData.cad("12.34")).then().statusCode(200);
        api.transfer(payee, payer, TestData.cad("62.34")).then().statusCode(201);
        rejectedRef = api.transfer(payee, payer, TestData.cad("9999.00")).then().statusCode(422).extract().path("transferReference");
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("API balances match the account table")
    void api_matches_database() {
        assertThat(db.balance(payer)).isEqualByComparingTo(api.balanceOf(payer)).isEqualByComparingTo("812.34");
        assertThat(db.balance(payee)).isEqualByComparingTo(api.balanceOf(payee)).isEqualByComparingTo("200.00");
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("A completed transfer writes exactly one debit and one credit of the same amount")
    void transfer_is_double_entry() {
        List<Map<String, Object>> entries = db.ledgerForTransfer(completedRef);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(e -> e.get("entry_type")).containsExactly("DEBIT", "CREDIT");
        assertThat(entries).extracting(e -> e.get("account_number")).containsExactly(payer, payee);
        assertThat(entries).allSatisfy(e -> assertThat((java.math.BigDecimal) e.get("amount")).isEqualByComparingTo("250.00"));
    }

    @Test
    @DisplayName("A rejected transfer is stored with its reason and moves no money")
    void rejected_transfer_row() {
        Map<String, Object> row = db.transfer(rejectedRef);

        assertThat(row).containsEntry("status", "REJECTED").containsEntry("failure_reason", "INSUFFICIENT_FUNDS");
        assertThat(db.ledgerForTransfer(rejectedRef)).isEmpty();
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Every account balance reconciles to the sum of its ledger entries")
    void ledger_reconciles_for_all_accounts() {
        assertThat(db.reconciliationBreaks()).as("accounts whose balance != sum(ledger)").isEmpty();
    }

    @Test
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Every completed transfer in the database is double-entry balanced")
    void all_transfers_balanced() {
        assertThat(db.unbalancedTransfers()).isEmpty();
    }

    @Test
    @DisplayName("No rejected transfer has ledger entries and every one has a reason")
    void all_rejections_clean() {
        assertThat(db.rejectedTransfersWithLedgerEntries()).isEmpty();
        assertThat(db.rejectedTransfersWithoutReason()).isEmpty();
    }

    @Test
    @DisplayName("No account has a negative balance")
    void no_negative_balances() {
        assertThat(db.negativeBalances()).isEmpty();
    }

    @Test
    @DisplayName("Running balance on each ledger entry follows from the previous one")
    void running_balance_chain() {
        assertThat(db.runningBalanceBreaks(payer)).isEmpty();
        assertThat(db.runningBalanceBreaks(payee)).isEmpty();
    }
}
