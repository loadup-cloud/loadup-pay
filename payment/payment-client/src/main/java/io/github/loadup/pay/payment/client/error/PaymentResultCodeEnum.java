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
    DEPENDENCY_UNAVAILABLE("PAYMENT_DEPENDENCY_UNAVAILABLE", "Payment dependency is unavailable"),
    STATEMENT_NOT_READY("PAYMENT_STATEMENT_NOT_READY", "Official statement is being generated"),
    STATEMENT_NOT_FOUND("PAYMENT_STATEMENT_NOT_FOUND", "Official statement is unavailable; no empty source is inferred"),
    LIMIT_EXCEEDED("PAYMENT_LIMIT_EXCEEDED", "Merchant limit exceeded"),
    LIMIT_RESERVATION_EXPIRED("PAYMENT_LIMIT_RESERVATION_EXPIRED", "Merchant limit reservation expired before submission"),
    EXECUTION_PERMIT_INVALID("PAYMENT_EXECUTION_PERMIT_INVALID", "Execution permit does not match the original payment attempt"),
    CHANNEL_BINDING_UNAVAILABLE("PAYMENT_CHANNEL_BINDING_UNAVAILABLE", "No active channel binding matches the frozen payment terms"),
    ROUTING_PROFILE_UNAVAILABLE("PAYMENT_ROUTING_PROFILE_UNAVAILABLE", "Routing profile is missing or belongs to another collecting merchant"),
    PAYMENT_EXPIRED("PAYMENT_EXPIRED", "The frozen payment deadline has expired"),
    INPUT_EXPIRED("PAYMENT_INPUT_EXPIRED", "Prepared channel input has expired"),
    INPUT_ALREADY_BOUND("PAYMENT_INPUT_ALREADY_BOUND", "Prepared input is bound to another attempt"),
    INPUT_UNAVAILABLE("PAYMENT_INPUT_UNAVAILABLE", "Secure channel input storage is unavailable"),
    INVALID_PAGE("PAYMENT_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("PAYMENT_NOT_FOUND", "Payment or refund not found"),
    IDEMPOTENCY_CONFLICT("PAYMENT_IDEMPOTENCY_CONFLICT", "Idempotency key payload changed"),
    ACTION_FORBIDDEN("PAYMENT_ACTION_FORBIDDEN", "Action is not permitted"),
    INVALID_STATE("PAYMENT_INVALID_STATE", "Invalid payment state"),
    SNAPSHOT_INVALID("PAYMENT_SNAPSHOT_INVALID", "Stored payment intent is invalid"),
    VERSION_CONFLICT("PAYMENT_VERSION_CONFLICT", "Observed recovery task metadata changed"),
    CONCURRENT_UPDATE("PAYMENT_CONCURRENT_UPDATE", "Concurrent payment update"),
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
