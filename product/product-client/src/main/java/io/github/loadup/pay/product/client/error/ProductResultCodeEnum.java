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
package io.github.loadup.pay.product.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum ProductResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("PRODUCT_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("PRODUCT_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("PRODUCT_NOT_FOUND", "Admission not found"),
    CONTRACT_NOT_ADMITTING("PRODUCT_CONTRACT_NOT_ADMITTING", "Contract is not admitting new orders"),
    POLICY_NOT_ACTIVE("PRODUCT_POLICY_NOT_ACTIVE", "Limit policy is not active"),
    IDEMPOTENCY_CONFLICT("PRODUCT_IDEMPOTENCY_CONFLICT", "Admission payload changed"),
    UNSUPPORTED_POLICY("PRODUCT_UNSUPPORTED_POLICY", "Policy is not implemented");

    private final String code;
    private final String message;

    ProductResultCodeEnum(String code, String message) {
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
