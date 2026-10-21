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
package io.github.loadup.pay.realtime.client.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum RealtimeResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("REALTIME_INVALID_ARGUMENT", "Invalid argument"),
    INVALID_PAGE("REALTIME_INVALID_PAGE", "Invalid page"),
    NOT_FOUND("REALTIME_NOT_FOUND", "Trade projection not found"),
    QUERY_FORBIDDEN("REALTIME_QUERY_FORBIDDEN", "Merchant query scope is not authorized");

    private final String code;
    private final String message;

    RealtimeResultCodeEnum(String code, String message) {
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
