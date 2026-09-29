package dev.suhaib.bank.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.suhaib.bank.account.Account;
import dev.suhaib.bank.account.AccountRepository;
import dev.suhaib.bank.account.AccountService;
import dev.suhaib.bank.account.AccountType;
import dev.suhaib.bank.common.BankException;
import dev.suhaib.bank.common.BankProperties;
import dev.suhaib.bank.common.ErrorCode;
import dev.suhaib.bank.ledger.LedgerEntry;
import dev.suhaib.bank.ledger.LedgerRepository;
import dev.suhaib.bank.transfer.TransferDtos.TransferRequest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** Fast unit tests for the transfer rules; the bank-tests module covers the same rules end to end. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferServiceTest {

    @Mock AccountService accountService;
    @Mock AccountRepository accounts;
    @Mock TransferRepository transfers;
    @Mock LedgerRepository ledger;

    private TransferService service;
    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        service = new TransferService(accountService, accounts, transfers, ledger, new BankProperties(new BigDecimal("10000.00")));
        alice = account(1L, "1111111111", "500.00");
        bob = account(2L, "2222222222", "0.00");
    }

    private Account account(long id, String number, String balance) {
        Account account = new Account(number, "Owner " + id, AccountType.CHEQUING);
        ReflectionTestUtils.setField(account, "id", id);
        account.credit(new BigDecimal(balance));
        when(accountService.idOf(number)).thenReturn(id);
        when(accounts.lockById(id)).thenReturn(Optional.of(account));
        return account;
    }

    private static TransferRequest request(String from, String to, String amount) {
        return new TransferRequest(from, to, new BigDecimal(amount), null);
    }

    @Test
    @DisplayName("A valid transfer debits, credits and writes two ledger entries")
    void completes_transfer() {
        TransferService.Result result = service.transfer(request("1111111111", "2222222222", "120.50"), null);

        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(alice.getBalance()).isEqualByComparingTo("379.50");
        assertThat(bob.getBalance()).isEqualByComparingTo("120.50");
        verify(ledger, times(2)).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Accounts are always locked lowest id first, whatever the direction")
    void locks_in_id_order() {
        bob.credit(new BigDecimal("1.00"));

        service.transfer(request("2222222222", "1111111111", "0.01"), null);

        InOrder order = inOrder(accounts);
        order.verify(accounts).lockById(1L);
        order.verify(accounts).lockById(2L);
    }

    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "1111111111, 2222222222, 500.01,   INSUFFICIENT_FUNDS",
            "1111111111, 2222222222, 10000.01, LIMIT_EXCEEDED",
            "1111111111, 1111111111, 1.00,     SAME_ACCOUNT",
    })
    @DisplayName("Business-rule failures store a REJECTED transfer and move no money")
    void rejects(String from, String to, String amount, ErrorCode expected) {
        assertThatThrownBy(() -> service.transfer(request(from, to, amount), null))
                .isInstanceOf(TransferRejectedException.class)
                .extracting(e -> ((BankException) e).code()).isEqualTo(expected);

        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transfers).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(saved.getValue().getFailureReason()).isEqualTo(expected);
        verify(ledger, never()).save(any());
        assertThat(alice.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("A frozen payee blocks the transfer")
    void frozen_payee() {
        bob.freeze();

        assertThatThrownBy(() -> service.transfer(request("1111111111", "2222222222", "1.00"), null))
                .extracting(e -> ((BankException) e).code()).isEqualTo(ErrorCode.ACCOUNT_FROZEN);
    }

    @Test
    @DisplayName("A replayed Idempotency-Key returns the stored transfer without touching balances")
    void replay_returns_original() {
        Transfer original = new Transfer("key-1", alice, bob, new BigDecimal("50.00"), null);
        when(transfers.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        TransferService.Result result = service.transfer(request("1111111111", "2222222222", "50.00"), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transfer()).isSameAs(original);
        verify(accounts, never()).lockById(any());
        assertThat(alice.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Reusing a key with a different amount is an idempotency conflict")
    void replay_with_different_payload() {
        Transfer original = new Transfer("key-2", alice, bob, new BigDecimal("50.00"), null);
        when(transfers.findByIdempotencyKey("key-2")).thenReturn(Optional.of(original));

        assertThatThrownBy(() -> service.transfer(request("1111111111", "2222222222", "60.00"), "key-2"))
                .extracting(e -> ((BankException) e).code()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
}
