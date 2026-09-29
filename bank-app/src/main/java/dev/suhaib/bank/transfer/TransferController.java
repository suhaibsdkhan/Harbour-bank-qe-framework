package dev.suhaib.bank.transfer;

import dev.suhaib.bank.transfer.TransferDtos.TransferRequest;
import dev.suhaib.bank.transfer.TransferDtos.TransferResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    /** Returns 201 for a new transfer and 200 when an Idempotency-Key replays an earlier one. */
    @PostMapping
    ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        TransferService.Result result = service.transfer(request, key);
        TransferResponse body = TransferResponse.from(result.transfer());
        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/transfers/" + body.reference())).body(body);
    }

    @GetMapping("/{reference}")
    TransferResponse get(@PathVariable String reference) {
        return TransferResponse.from(service.get(reference));
    }
}
