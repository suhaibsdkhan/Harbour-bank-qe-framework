package dev.suhaib.bank.common;

import org.springframework.http.HttpStatus;

/** Stable, machine-readable error codes returned in the {@code code} field of every error response. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY),
    SAME_ACCOUNT(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
