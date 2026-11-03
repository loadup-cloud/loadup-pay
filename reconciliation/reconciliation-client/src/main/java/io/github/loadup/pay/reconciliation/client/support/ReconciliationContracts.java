package io.github.loadup.pay.reconciliation.client.support;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum;

/** Protocol-independent checks for frozen reconciliation evidence. */
public final class ReconciliationContracts {
    private ReconciliationContracts() {}

    public static void require(boolean valid) {
        if (!valid) {
            throw new CommonException(ReconciliationResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static void text(String value, int limit) {
        require(value != null
                && !value.isBlank()
                && value.length() <= limit
                && value.chars().noneMatch(Character::isISOControl));
    }

    public static void hash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"));
    }
}
