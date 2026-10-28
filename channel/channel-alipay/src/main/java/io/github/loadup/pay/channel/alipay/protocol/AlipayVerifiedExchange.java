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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/**
 * Private signed API envelope plus one-way request binding. Never retains the form body's delegated token.
 */
public record AlipayVerifiedExchange(AlipayVerifiedResponse response, String requestSha256,
                                     @DiagnosticHidden byte[] responseBytes) {
    public AlipayVerifiedExchange {
        if (response == null || requestSha256 == null || !requestSha256.matches("[0-9a-f]{64}")
                || responseBytes == null || responseBytes.length == 0 || responseBytes.length > 1048576)
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        responseBytes = responseBytes.clone();
    }

    @Override
    public byte[] responseBytes() {
        return responseBytes.clone();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
