/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum PaymentResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("PAYMENT_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("PAYMENT_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("PAYMENT_NOT_FOUND", "Payment or refund not found"),
    IDEMPOTENCY_CONFLICT("PAYMENT_IDEMPOTENCY_CONFLICT", "Idempotency key payload changed"),
    OUTCOME_UNKNOWN("PAYMENT_OUTCOME_UNKNOWN", "An earlier attempt may still collect funds"),
    EXECUTION_FENCED("PAYMENT_EXECUTION_FENCED", "Payment execution is fenced"),
    REFUND_AMOUNT_EXCEEDED("PAYMENT_REFUND_AMOUNT_EXCEEDED", "Refund exceeds available original payment amount"),
    UNSUPPORTED_CAPABILITY("PAYMENT_UNSUPPORTED_CAPABILITY", "Channel capability is not implemented or enabled");

    private final String code;
    private final String message;

    PaymentResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getStatus() {
        return ResultStatusEnum.FAIL.getCode();
    }
}
