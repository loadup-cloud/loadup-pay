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
package io.github.loadup.pay.limitcenter.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum LimitCenterResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("LIMIT_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("LIMIT_INVALID_PAGE", "Invalid page"),
    EXCEEDED("LIMIT_EXCEEDED", "Merchant limit exceeded"),
    POLICY_NOT_ACTIVE("LIMIT_POLICY_NOT_ACTIVE", "Limit policy is not active"),
    EXECUTION_FENCED("LIMIT_EXECUTION_FENCED", "Limit operation is fenced"),
    RESERVATION_EXPIRED("LIMIT_RESERVATION_EXPIRED", "Reservation expired before execution"),
    DEPENDENCY_NOT_READY("LIMIT_DEPENDENCY_NOT_READY", "Original payment confirmation is not ready"),
    IDEMPOTENCY_CONFLICT("LIMIT_IDEMPOTENCY_CONFLICT", "Limit operation payload changed"),
    OUTCOME_CONFLICT("LIMIT_OUTCOME_CONFLICT", "Payment outcome conflicts with stored evidence");

    private final String code;
    private final String message;

    LimitCenterResultCodeEnum(String code, String message) {
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
