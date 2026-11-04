/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.merchantcenter.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum MerchantResultCodeEnum implements ResultCode {
    NOT_FOUND("MERCHANT_NOT_FOUND", "Merchant not found"),
    CONFLICT("MERCHANT_CONFLICT", "Merchant code exists or version changed"),
    ID_REQUIRED("MERCHANT_ID_REQUIRED", "Merchant id required"),
    MERCHANT_IDENTITY_MISMATCH("MERCHANT_MERCHANT_IDENTITY_MISMATCH", "Merchant identity mismatch"),
    INVALID_PAGE("MERCHANT_INVALID_PAGE", "Invalid page"),
    AUTHENTICATED_ACTOR_REQUIRED("MERCHANT_AUTHENTICATED_ACTOR_REQUIRED", "Authenticated actor required"),
    REQUIRED_MERCHANT_FIELD_MISSING("MERCHANT_REQUIRED_MERCHANT_FIELD_MISSING", "Required merchant field missing"),
    INVALID_MERCHANT_CLASSIFICATION(
            "MERCHANT_INVALID_MERCHANT_CLASSIFICATION", "Invalid merchant code, industry or country"),
    INVALID_CONTACT_PHONE("MERCHANT_INVALID_CONTACT_PHONE", "Invalid contact phone"),
    INVALID_CONTACT_EMAIL("MERCHANT_INVALID_CONTACT_EMAIL", "Invalid contact email"),
    INVALID_MERCHANT_FIELD("MERCHANT_INVALID_MERCHANT_FIELD", "Invalid merchant field length or control character"),
    INVALID_MERCHANT_VERSION("MERCHANT_INVALID_MERCHANT_VERSION", "Invalid merchant version"),
    MERCHANT_CODE_IS_IMMUTABLE("MERCHANT_MERCHANT_CODE_IS_IMMUTABLE", "Merchant code is immutable");
    private final String code;
    private final String message;

    MerchantResultCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return ResultStatusEnum.FAIL.getCode();
    }
}
