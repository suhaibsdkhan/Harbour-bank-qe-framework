package dev.suhaib.bank.account;

import dev.suhaib.bank.account.AccountDtos.AccountResponse;
import dev.suhaib.bank.account.AccountDtos.DepositRequest;
import dev.suhaib.bank.account.AccountDtos.OpenAccountRequest;
import dev.suhaib.bank.ledger.LedgerEntryResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountResponse body = AccountResponse.from(service.open(request));
        return ResponseEntity.created(URI.create("/api/accounts/" + body.accountNumber())).body(body);
    }

    @GetMapping
    List<AccountResponse> list() {
        return service.findAll().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{accountNumber}")
    AccountResponse get(@PathVariable String accountNumber) {
        return AccountResponse.from(service.get(accountNumber));
    }

    @PostMapping("/{accountNumber}/deposits")
    AccountResponse deposit(@PathVariable String accountNumber, @Valid @RequestBody DepositRequest request) {
        return AccountResponse.from(service.deposit(accountNumber, request.amount()));
    }

    @PostMapping("/{accountNumber}/freeze")
    AccountResponse freeze(@PathVariable String accountNumber) {
        return AccountResponse.from(service.freeze(accountNumber));
    }

    @GetMapping("/{accountNumber}/transactions")
    List<LedgerEntryResponse> transactions(@PathVariable String accountNumber) {
        return service.history(accountNumber).stream().map(LedgerEntryResponse::from).toList();
    }
}
