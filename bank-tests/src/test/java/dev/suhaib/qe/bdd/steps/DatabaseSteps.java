package dev.suhaib.qe.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import dev.suhaib.qe.bdd.ScenarioContext;
import dev.suhaib.qe.db.BankDb;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;

public class DatabaseSteps {

    private final ScenarioContext ctx;

    public DatabaseSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Before("@db")
    public void requireDatabase() {
        Assumptions.assumeTrue(BankDb.isConfigured(), "db.url is not set");
    }

    @Then("the database should hold a {word} transfer of ${bigdecimal}")
    public void transferRow(String status, BigDecimal amount) {
        Map<String, Object> row = ctx.db.transfer(ctx.lastTransferReference);
        assertThat(row).containsEntry("status", status);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(amount);
    }

    @Then("the database should record the failure reason {word}")
    public void failureReason(String reason) {
        assertThat(ctx.db.transfer(ctx.lastTransferReference)).containsEntry("failure_reason", reason);
    }

    @Then("the ledger should contain {int} entries for the transfer")
    public void ledgerEntries(int count) {
        assertThat(ctx.db.ledgerForTransfer(ctx.lastTransferReference)).hasSize(count);
    }

    @Then("the ledger should show a DEBIT for {word} and a CREDIT for {word}")
    public void debitAndCredit(String from, String to) {
        List<Map<String, Object>> entries = ctx.db.ledgerForTransfer(ctx.lastTransferReference);
        assertThat(entries).extracting(e -> e.get("entry_type") + ":" + e.get("account_number"))
                .containsExactly("DEBIT:" + ctx.account(from), "CREDIT:" + ctx.account(to));
    }

    @Then("{word}'s balance in the database should be ${bigdecimal}")
    public void dbBalance(String alias, BigDecimal expected) {
        assertThat(ctx.db.balance(ctx.account(alias))).isEqualByComparingTo(expected);
    }

    @Then("every account should reconcile with the ledger")
    public void reconcile() {
        assertThat(ctx.db.reconciliationBreaks()).isEmpty();
        assertThat(ctx.db.unbalancedTransfers()).isEmpty();
    }
}
