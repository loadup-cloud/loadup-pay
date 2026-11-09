package io.github.loadup.reconciliation.app.model.payment.enums;

public enum PaymentCaptureBlocker {
    UNRESOLVED_COLLECTION,
    REFUND_ACCEPTANCE_MISSING,
    REFUND_ACCEPTANCE_CONFLICT,
    RECORD_LIMIT,
    TOTAL_OVERFLOW,
    ACCOUNT_SCOPE_UNKNOWN
}
