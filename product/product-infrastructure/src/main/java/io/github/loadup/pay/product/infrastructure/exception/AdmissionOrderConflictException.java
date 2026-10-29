package io.github.loadup.pay.product.infrastructure.exception;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;

/**
 * Another transaction has claimed the same tenant and order.
 */
public final class AdmissionOrderConflictException extends CommonException {
    public AdmissionOrderConflictException() {
        super(ProductResultCodeEnum.IDEMPOTENCY_CONFLICT);
    }
}
