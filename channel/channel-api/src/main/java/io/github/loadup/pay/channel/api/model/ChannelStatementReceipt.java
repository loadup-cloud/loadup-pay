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

import java.time.OffsetDateTime;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/**
 * Private provenance bytes retain signed grant evidence; they may contain short-lived bearer URLs and are never browser data.
 */
public record ChannelStatementReceipt(String format, String requestSha256, String evidenceSha256,
                                      String officialHashType, String officialHash,
                                      String contentSha256, OffsetDateTime receivedAt, OffsetDateTime downloadedAt,
                                      @DiagnosticHidden byte[] evidence) {
    public ChannelStatementReceipt {
        if (format == null || format.isBlank() || format.length() > 64 || !hash(requestSha256) || !hash(evidenceSha256) || !hash(contentSha256)
                || officialHashType == null || officialHash == null || !officialHash.matches("[0-9a-f]{1,128}")
                || receivedAt == null || downloadedAt == null || downloadedAt.isBefore(receivedAt) || evidence == null || evidence.length == 0 || evidence.length > 32768) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        evidence = evidence.clone();
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    @Override
    public byte[] evidence() {
        return evidence.clone();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
