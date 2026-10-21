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
package io.github.loadup.pay.customer.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum CustomerResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("CUSTOMER_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("CUSTOMER_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("CUSTOMER_NOT_FOUND", "Merchant not found"),
    ACTION_FORBIDDEN("CUSTOMER_ACTION_FORBIDDEN", "Merchant action is not authorized"),
    RELATIONSHIP_CONFLICT("CUSTOMER_RELATIONSHIP_CONFLICT", "Merchant relationship conflicts"),
    MERCHANT_INACTIVE("CUSTOMER_MERCHANT_INACTIVE", "Merchant is not active");

    private final String code;
    private final String message;

    CustomerResultCodeEnum(String code, String message) {
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
