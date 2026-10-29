package io.github.loadup.pay.product.infrastructure.exception;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;

/**
 * A concurrent publication claimed one of the expected business keys.
 */
public final class PublicationKeyConflictException extends CommonException {
    public PublicationKeyConflictException(Throwable cause) {
        super(ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT, cause);
    }
}
