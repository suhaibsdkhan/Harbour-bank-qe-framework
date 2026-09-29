package dev.suhaib.bank.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record OpenAccountRequest(
            @NotBlank @Size(max = 100) String ownerName,
            @NotNull AccountType type,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal initialDeposit) {
    }

    public record DepositRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal amount) {
    }

    public record AccountResponse(
            String accountNumber,
            String ownerName,
            AccountType type,
            String currency,
            BigDecimal balance,
            AccountStatus status,
            Instant createdAt) {

        static AccountResponse from(Account a) {
            return new AccountResponse(a.getAccountNumber(), a.getOwnerName(), a.getAccountType(),
                    a.getCurrency(), a.getBalance(), a.getStatus(), a.getCreatedAt());
        }
    }
}
