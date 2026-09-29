package dev.suhaib.bank.transfer;

import dev.suhaib.bank.common.ErrorCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record TransferRequest(
            @NotBlank String fromAccount,
            @NotBlank String toAccount,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @Size(max = 140) String description) {
    }

    public record TransferResponse(
            String reference,
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String currency,
            String description,
            TransferStatus status,
            ErrorCode failureReason,
            Instant createdAt) {

        static TransferResponse from(Transfer t) {
            return new TransferResponse(t.getReference(), t.getFromAccount().getAccountNumber(),
                    t.getToAccount().getAccountNumber(), t.getAmount(), t.getFromAccount().getCurrency(),
                    t.getDescription(), t.getStatus(), t.getFailureReason(), t.getCreatedAt());
        }
    }
}
