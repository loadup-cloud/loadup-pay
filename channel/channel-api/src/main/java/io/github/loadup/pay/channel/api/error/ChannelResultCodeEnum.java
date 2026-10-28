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
package io.github.loadup.pay.channel.api.error;

import io.github.loadup.commons.enums.ResultStatusEnum;
import io.github.loadup.commons.result.ResultCode;

public enum ChannelResultCodeEnum implements ResultCode {
    INVALID_ARGUMENT("CHANNEL_INVALID_ARGUMENT", "Invalid channel request"),
    INVALID_STATE("CHANNEL_INVALID_STATE", "External channel calls cannot run inside a local transaction"),
    ACTION_FORBIDDEN("CHANNEL_ACTION_FORBIDDEN", "Channel tenant context is invalid"),
    CHANNEL_BINDING_MISMATCH("CHANNEL_BINDING_MISMATCH", "Channel identity differs from the frozen collecting account"),
    DUPLICATE_PROVIDER("CHANNEL_DUPLICATE_PROVIDER", "More than one provider is registered for a channel"),
    UNSUPPORTED_CAPABILITY("CHANNEL_UNSUPPORTED_CAPABILITY", "Channel capability is not implemented or enabled"),
    DEPENDENCY_UNAVAILABLE("CHANNEL_DEPENDENCY_UNAVAILABLE", "Channel dependency is unavailable"),
    RECOVERY_BUSY("CHANNEL_RECOVERY_BUSY", "Channel recovery concurrency limit reached"),
    RECOVERY_CONFIGURATION_INVALID("CHANNEL_RECOVERY_CONFIGURATION_INVALID", "Channel recovery requires nonblocking concurrency limits"),
    CHANNEL_CREDENTIAL_UNAVAILABLE("CHANNEL_CREDENTIAL_UNAVAILABLE", "Frozen channel credential resource is unavailable"),
    CHANNEL_SIGNATURE_INVALID("CHANNEL_SIGNATURE_INVALID", "Channel response signature is invalid"),
    ACTION_SNAPSHOT_INVALID("CHANNEL_ACTION_SNAPSHOT_INVALID", "Channel action snapshot is invalid"),
    STATEMENT_RATE_LIMITED("CHANNEL_STATEMENT_RATE_LIMITED", "Official statement request is rate limited"),
    STATEMENT_NOT_READY("CHANNEL_STATEMENT_NOT_READY", "Official statement is still being generated"),
    STATEMENT_NOT_FOUND("CHANNEL_STATEMENT_NOT_FOUND", "Official statement is unavailable; no empty statement is inferred"),
    CHANNEL_RESPONSE_INVALID("CHANNEL_RESPONSE_INVALID", "Channel response violates the expected protocol");

    private final String code;
    private final String message;

    ChannelResultCodeEnum(String code, String message) {
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

