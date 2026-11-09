package io.github.loadup.reconciliation.app.model.payment.enums;

public enum PaymentVerificationOutcome {
    ACCEPTED,
    SUCCEEDED,
    NO_COLLECTION,
    UNKNOWN,
    CLOSED_WITH_FUNDS_RETURNED,
    FAILED_WITH_NO_REFUND
}
