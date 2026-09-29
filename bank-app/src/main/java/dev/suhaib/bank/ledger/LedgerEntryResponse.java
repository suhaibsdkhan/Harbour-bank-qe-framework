package dev.suhaib.bank.ledger;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        EntryType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String transferReference,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry e) {
        String reference = e.getTransfer() == null ? null : e.getTransfer().getReference();
        return new LedgerEntryResponse(e.getEntryType(), e.getAmount(), e.getBalanceAfter(),
                e.getDescription(), reference, e.getCreatedAt());
    }
}
