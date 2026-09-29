package dev.suhaib.bank.transfer;

import dev.suhaib.bank.account.Account;
import dev.suhaib.bank.account.AccountRepository;
import dev.suhaib.bank.account.AccountService;
import dev.suhaib.bank.common.BankException;
import dev.suhaib.bank.common.BankProperties;
import dev.suhaib.bank.common.ErrorCode;
import dev.suhaib.bank.ledger.EntryType;
import dev.suhaib.bank.ledger.LedgerEntry;
import dev.suhaib.bank.ledger.LedgerRepository;
import dev.suhaib.bank.transfer.TransferDtos.TransferRequest;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    /** Outcome of a transfer call; {@code replayed} is true when an Idempotency-Key matched an earlier request. */
    public record Result(Transfer transfer, boolean replayed) {
    }

    private final AccountService accountService;
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerRepository ledger;
    private final BankProperties properties;

    public TransferService(AccountService accountService, AccountRepository accounts, TransferRepository transfers,
                           LedgerRepository ledger, BankProperties properties) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.properties = properties;
    }

    @Transactional(noRollbackFor = TransferRejectedException.class)
    public Result transfer(TransferRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transfer> previous = transfers.findByIdempotencyKey(idempotencyKey);
            if (previous.isPresent()) {
                return replay(previous.get(), request);
            }
        }

        Long fromId = accountService.idOf(request.fromAccount());
        Long toId = accountService.idOf(request.toAccount());

        // Always lock in ascending id order so two opposite transfers can never deadlock.
        Account first = accounts.lockById(Math.min(fromId, toId)).orElseThrow();
        Account second = fromId.equals(toId) ? first : accounts.lockById(Math.max(fromId, toId)).orElseThrow();
        Account from = fromId.equals(first.getId()) ? first : second;
        Account to = toId.equals(first.getId()) ? first : second;

        Transfer transfer = new Transfer(idempotencyKey, from, to, request.amount(), request.description());

        ErrorCode rejection = check(from, to, transfer);
        if (rejection != null) {
            transfer.reject(rejection);
            transfers.save(transfer);
            throw rejected(transfer);
        }

        from.debit(request.amount());
        to.credit(request.amount());
        transfers.save(transfer);
        String memo = request.description() == null ? "Transfer" : request.description();
        ledger.save(new LedgerEntry(from, transfer, EntryType.DEBIT, request.amount(), from.getBalance(), memo));
        ledger.save(new LedgerEntry(to, transfer, EntryType.CREDIT, request.amount(), to.getBalance(), memo));
        return new Result(transfer, false);
    }

    @Transactional(readOnly = true)
    public Transfer get(String reference) {
        return transfers.findByReference(reference).orElseThrow(() ->
                new BankException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer " + reference + " does not exist"));
    }

    private ErrorCode check(Account from, Account to, Transfer transfer) {
        if (from.getId().equals(to.getId())) {
            return ErrorCode.SAME_ACCOUNT;
        }
        if (from.isFrozen() || to.isFrozen()) {
            return ErrorCode.ACCOUNT_FROZEN;
        }
        if (transfer.getAmount().compareTo(properties.transferLimit()) > 0) {
            return ErrorCode.LIMIT_EXCEEDED;
        }
        if (from.getBalance().compareTo(transfer.getAmount()) < 0) {
            return ErrorCode.INSUFFICIENT_FUNDS;
        }
        return null;
    }

    private Result replay(Transfer previous, TransferRequest request) {
        if (!previous.matches(request.fromAccount(), request.toAccount(), request.amount())) {
            throw new BankException(ErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used with a different request");
        }
        if (previous.getStatus() == TransferStatus.REJECTED) {
            throw rejected(previous);
        }
        return new Result(previous, true);
    }

    private static TransferRejectedException rejected(Transfer t) {
        String message = switch (t.getFailureReason()) {
            case SAME_ACCOUNT -> "Cannot transfer to the same account";
            case ACCOUNT_FROZEN -> "One of the accounts is frozen";
            case LIMIT_EXCEEDED -> "Amount exceeds the single transfer limit";
            case INSUFFICIENT_FUNDS -> "Insufficient funds";
            default -> "Transfer rejected";
        };
        return new TransferRejectedException(t.getFailureReason(), message, t.getReference());
    }
}
