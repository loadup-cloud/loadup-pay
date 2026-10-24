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
package io.github.loadup.pay.acquire.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum AcquireResultCodeEnum implements ResultCode {
    UNSUPPORTED_CAPABILITY("ACQUIRE_UNSUPPORTED_CAPABILITY", "Payment input or method is not implemented"),
    PAYMENT_INPUT_EXPIRED("ACQUIRE_PAYMENT_INPUT_EXPIRED", "Original channel input expired; close the original payment safely before a new action"),
    INVALID_ARGUMENT("ACQUIRE_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("ACQUIRE_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("ACQUIRE_NOT_FOUND", "Resource not found"),
    IDEMPOTENCY_CONFLICT("ACQUIRE_IDEMPOTENCY_CONFLICT", "Idempotency key payload changed"),
    CONTRACT_NOT_ADMITTING("ACQUIRE_CONTRACT_NOT_ADMITTING", "Contract is not admitting new orders"),
    ORDER_NOT_PAYABLE("ACQUIRE_ORDER_NOT_PAYABLE", "Order cannot accept a new payment"),
    AMOUNT_EXCEEDED("ACQUIRE_AMOUNT_EXCEEDED", "Amount exceeds available order allocation"),
    VERSION_CONFLICT("ACQUIRE_VERSION_CONFLICT", "Resource version changed"),
    ACTION_FORBIDDEN("ACQUIRE_ACTION_FORBIDDEN", "Caller cannot perform this action"),
    INVALID_STATE("ACQUIRE_INVALID_STATE", "Operation cannot run in the current state"),
    DEPENDENCY_UNAVAILABLE("ACQUIRE_DEPENDENCY_UNAVAILABLE", "Acquiring dependency unavailable"),
    ADMISSION_EXPIRED("ACQUIRE_ADMISSION_EXPIRED", "Original admission materialization window expired"),
    SNAPSHOT_INVALID("ACQUIRE_SNAPSHOT_INVALID", "Stored order intent or snapshot is invalid"),
    CONCURRENT_UPDATE("ACQUIRE_CONCURRENT_UPDATE", "Concurrent update; retry with the original intent"),
    SESSION_UNAUTHORIZED("ACQUIRE_SESSION_UNAUTHORIZED", "Invalid or expired checkout session"),
    SESSION_UNAVAILABLE("ACQUIRE_SESSION_UNAVAILABLE", "Original checkout credential cannot be replayed");

    private final String code;
    private final String message;

    AcquireResultCodeEnum(String code, String message) {
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
