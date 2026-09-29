package dev.suhaib.bank.transfer;

import dev.suhaib.bank.account.Account;
import dev.suhaib.bank.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference = UUID.randomUUID().toString();

    @Column(unique = true)
    private String idempotencyKey;

    @ManyToOne
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Enumerated(EnumType.STRING)
    private ErrorCode failureReason;

    @Column(nullable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    protected Transfer() {
    }

    public Transfer(String idempotencyKey, Account from, Account to, BigDecimal amount, String description) {
        this.idempotencyKey = idempotencyKey;
        this.fromAccount = from;
        this.toAccount = to;
        this.amount = amount;
        this.description = description;
        this.status = TransferStatus.COMPLETED;
    }

    public void reject(ErrorCode reason) {
        this.status = TransferStatus.REJECTED;
        this.failureReason = reason;
    }

    /** True when a replayed request carries the same payload as the one that created this transfer. */
    public boolean matches(String from, String to, BigDecimal otherAmount) {
        return fromAccount.getAccountNumber().equals(from)
                && toAccount.getAccountNumber().equals(to)
                && amount.compareTo(otherAmount) == 0;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Account getFromAccount() {
        return fromAccount;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public ErrorCode getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
