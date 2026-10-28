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
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/**
 * Successful transport provenance only; the bytes still require a complete account/date/format/trailer parser.
 */
public record VerifiedChannelStatement(String bindingRevisionId, ChannelStatementScope scope,
                                       ChannelStatementReceipt receipt, @DiagnosticHidden byte[] content) {
    public VerifiedChannelStatement {
        if (bindingRevisionId == null || bindingRevisionId.isBlank() || bindingRevisionId.length() > 64 || scope == null || receipt == null
                || content == null || content.length == 0 || content.length > ChannelStatementRequest.MAX_BYTES)
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public int size() {
        return content.length;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
