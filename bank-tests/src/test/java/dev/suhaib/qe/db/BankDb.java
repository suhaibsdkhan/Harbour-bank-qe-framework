package dev.suhaib.qe.db;

import dev.suhaib.qe.config.TestConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.Step;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only SQL access to the bank's database for data-integrity checks.
 * Each query is attached to the Allure report so a reviewer can re-run it by hand.
 */
public class BankDb {

    public static boolean isConfigured() {
        return TestConfig.dbUrl().isPresent();
    }

    public List<Map<String, Object>> query(String sql, Object... params) {
        Allure.addAttachment("SQL", "text/plain", sql.strip());
        try (Connection c = DriverManager.getConnection(TestConfig.dbUrl().orElseThrow(), TestConfig.dbUser(), TestConfig.dbPassword());
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= md.getColumnCount(); col++) {
                        row.put(md.getColumnLabel(col).toLowerCase(), rs.getObject(col));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + sql, e);
        }
    }

    @Step("DB: balance of account {accountNumber}")
    public BigDecimal balance(String accountNumber) {
        return (BigDecimal) query("SELECT balance FROM account WHERE account_number = ?", accountNumber)
                .getFirst().get("balance");
    }

    @Step("DB: transfer row for {reference}")
    public Map<String, Object> transfer(String reference) {
        List<Map<String, Object>> rows = query("SELECT * FROM transfer WHERE reference = ?", reference);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    @Step("DB: ledger entries for transfer {reference}")
    public List<Map<String, Object>> ledgerForTransfer(String reference) {
        return query("""
                SELECT a.account_number, l.entry_type, l.amount, l.balance_after
                FROM ledger_entry l
                JOIN transfer t ON t.id = l.transfer_id
                JOIN account a ON a.id = l.account_id
                WHERE t.reference = ?
                ORDER BY l.id
                """, reference);
    }

    /** Accounts whose stored balance differs from the sum of their ledger entries. */
    @Step("DB: accounts whose balance does not reconcile to the ledger")
    public List<Map<String, Object>> reconciliationBreaks() {
        return query("""
                SELECT a.account_number, a.balance,
                       COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE -l.amount END), 0) AS ledger_total
                FROM account a
                LEFT JOIN ledger_entry l ON l.account_id = a.id
                GROUP BY a.id, a.account_number, a.balance
                HAVING a.balance <> COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE -l.amount END), 0)
                """);
    }

    /** Completed transfers that do not have exactly one debit and one credit of the transfer amount. */
    @Step("DB: completed transfers that are not double-entry balanced")
    public List<Map<String, Object>> unbalancedTransfers() {
        return query("""
                SELECT t.reference, t.amount,
                       SUM(CASE WHEN l.entry_type = 'DEBIT' THEN 1 ELSE 0 END) AS debits,
                       SUM(CASE WHEN l.entry_type = 'CREDIT' THEN 1 ELSE 0 END) AS credits,
                       COALESCE(SUM(CASE WHEN l.entry_type = 'DEBIT' THEN l.amount ELSE 0 END), 0) AS debit_total,
                       COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE 0 END), 0) AS credit_total
                FROM transfer t
                LEFT JOIN ledger_entry l ON l.transfer_id = t.id
                WHERE t.status = 'COMPLETED'
                GROUP BY t.id, t.reference, t.amount
                HAVING SUM(CASE WHEN l.entry_type = 'DEBIT' THEN 1 ELSE 0 END) <> 1
                    OR SUM(CASE WHEN l.entry_type = 'CREDIT' THEN 1 ELSE 0 END) <> 1
                    OR COALESCE(SUM(CASE WHEN l.entry_type = 'DEBIT' THEN l.amount ELSE 0 END), 0) <> t.amount
                    OR COALESCE(SUM(CASE WHEN l.entry_type = 'CREDIT' THEN l.amount ELSE 0 END), 0) <> t.amount
                """);
    }

    /** Rejected transfers must never move money. */
    @Step("DB: rejected transfers that have ledger entries")
    public List<Map<String, Object>> rejectedTransfersWithLedgerEntries() {
        return query("""
                SELECT t.reference, COUNT(l.id) AS entries
                FROM transfer t
                JOIN ledger_entry l ON l.transfer_id = t.id
                WHERE t.status = 'REJECTED'
                GROUP BY t.reference
                """);
    }

    @Step("DB: rejected transfers without a failure reason")
    public List<Map<String, Object>> rejectedTransfersWithoutReason() {
        return query("SELECT reference FROM transfer WHERE status = 'REJECTED' AND failure_reason IS NULL");
    }

    @Step("DB: accounts with a negative balance")
    public List<Map<String, Object>> negativeBalances() {
        return query("SELECT account_number, balance FROM account WHERE balance < 0");
    }

    /**
     * Every ledger entry's balance_after must equal the previous entry's balance_after plus or minus
     * its own amount, i.e. the running balance never skips or double-applies a movement.
     */
    @Step("DB: ledger entries that break the running-balance chain for {accountNumber}")
    public List<Map<String, Object>> runningBalanceBreaks(String accountNumber) {
        List<Map<String, Object>> entries = query("""
                SELECT l.id, l.entry_type, l.amount, l.balance_after
                FROM ledger_entry l JOIN account a ON a.id = l.account_id
                WHERE a.account_number = ?
                ORDER BY l.id
                """, accountNumber);
        List<Map<String, Object>> breaks = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map<String, Object> e : entries) {
            BigDecimal amount = (BigDecimal) e.get("amount");
            running = "CREDIT".equals(e.get("entry_type")) ? running.add(amount) : running.subtract(amount);
            if (running.compareTo((BigDecimal) e.get("balance_after")) != 0) {
                breaks.add(e);
            }
        }
        return breaks;
    }
}
