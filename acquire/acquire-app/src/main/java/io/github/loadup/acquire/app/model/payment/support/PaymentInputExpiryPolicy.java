package io.github.loadup.acquire.app.model.payment.support;

/**
 * Stable identities of conditional input/preparation-expiry closes; retries never create a new control request.
 */
public final class PaymentInputExpiryPolicy {
    public static final String REQUEST_KEY_PREFIX = "input-expiry/";
    public static final String REASON = "INPUT_EXPIRED";
    public static final String PREPARATION_REQUEST_KEY_PREFIX = "input-preparation-expiry/";
    public static final String PREPARATION_REASON = "INPUT_PREPARATION_EXPIRED";
    public static final long FENCE_EPOCH = 1L;

    private PaymentInputExpiryPolicy() {}
}
