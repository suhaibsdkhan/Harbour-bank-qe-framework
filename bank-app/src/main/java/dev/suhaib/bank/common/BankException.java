package dev.suhaib.bank.common;

import java.util.Map;

public class BankException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public BankException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BankException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
