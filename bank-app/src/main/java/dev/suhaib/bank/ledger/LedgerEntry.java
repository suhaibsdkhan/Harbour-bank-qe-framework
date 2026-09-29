package dev.suhaib.bank.ledger;

import dev.suhaib.bank.account.Account;
import dev.suhaib.bank.transfer.Transfer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** One side of a money movement. Every transfer writes exactly one DEBIT and one CREDIT. */
@Entity
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryType entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    private String description;

    @Column(nullable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    protected LedgerEntry() {
    }

    public LedgerEntry(Account account, Transfer transfer, EntryType entryType, BigDecimal amount,
                       BigDecimal balanceAfter, String description) {
        this.account = account;
        this.transfer = transfer;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Transfer getTransfer() {
        return transfer;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
