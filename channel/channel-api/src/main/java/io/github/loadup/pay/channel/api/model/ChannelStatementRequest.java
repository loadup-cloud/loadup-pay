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
package io.github.loadup.pay.channel.api.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;

/**
 * Bounded raw official statement; parsing and comparison belong to reconciliation.
 */
public record ChannelStatementRequest(ChannelStatementScope scope, int maxBytes) {
    public static final int MAX_BYTES = 16 * 1024 * 1024;

    public ChannelStatementRequest {
        if (scope == null || maxBytes < 1 || maxBytes > MAX_BYTES) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
