package dev.suhaib.bank.transfer;

import dev.suhaib.bank.common.BankException;
import dev.suhaib.bank.common.ErrorCode;
import java.util.Map;

/** A business-rule rejection. The REJECTED transfer row is still committed for the audit trail. */
public class TransferRejectedException extends BankException {

    public TransferRejectedException(ErrorCode code, String message, String reference) {
        super(code, message, Map.of("transferReference", reference));
    }
}
