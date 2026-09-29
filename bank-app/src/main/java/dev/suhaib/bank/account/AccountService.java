package dev.suhaib.bank.account;

import dev.suhaib.bank.account.AccountDtos.OpenAccountRequest;
import dev.suhaib.bank.common.BankException;
import dev.suhaib.bank.common.ErrorCode;
import dev.suhaib.bank.ledger.EntryType;
import dev.suhaib.bank.ledger.LedgerEntry;
import dev.suhaib.bank.ledger.LedgerRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    public AccountService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public Account open(OpenAccountRequest request) {
        Account account = accounts.save(new Account(newAccountNumber(), request.ownerName().trim(), request.type()));
        BigDecimal initial = request.initialDeposit();
        if (initial != null && initial.signum() > 0) {
            account.credit(initial);
            ledger.save(new LedgerEntry(account, null, EntryType.CREDIT, initial, account.getBalance(), "Initial deposit"));
        }
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accounts.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Account get(String accountNumber) {
        return accounts.findByAccountNumber(accountNumber).orElseThrow(() -> notFound(accountNumber));
    }

    @Transactional
    public Account deposit(String accountNumber, BigDecimal amount) {
        Account account = lock(accountNumber);
        if (account.isFrozen()) {
            throw new BankException(ErrorCode.ACCOUNT_FROZEN, "Account " + accountNumber + " is frozen");
        }
        account.credit(amount);
        ledger.save(new LedgerEntry(account, null, EntryType.CREDIT, amount, account.getBalance(), "Deposit"));
        return account;
    }

    @Transactional
    public Account freeze(String accountNumber) {
        Account account = lock(accountNumber);
        account.freeze();
        return account;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> history(String accountNumber) {
        Account account = get(accountNumber);
        return ledger.findByAccountIdOrderByIdDesc(account.getId());
    }

    public Long idOf(String accountNumber) {
        return accounts.findIdByAccountNumber(accountNumber).orElseThrow(() -> notFound(accountNumber));
    }

    private Account lock(String accountNumber) {
        return accounts.lockById(idOf(accountNumber)).orElseThrow(() -> notFound(accountNumber));
    }

    static BankException notFound(String accountNumber) {
        return new BankException(ErrorCode.ACCOUNT_NOT_FOUND, "Account " + accountNumber + " does not exist");
    }

    private String newAccountNumber() {
        String candidate;
        do {
            candidate = String.format("%010d", RANDOM.nextLong(1_000_000_000L, 10_000_000_000L));
        } while (accounts.existsByAccountNumber(candidate));
        return candidate;
    }
}
