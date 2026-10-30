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

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/**
 * Ephemeral API grant only. This type neither authorizes an arbitrary fetch nor proves an empty/successful statement.
 */
public record AlipayStatementGrant(String bindingRevisionId, String accountScopeHash, LocalDate billDate,
                                   OffsetDateTime requestedAt, OffsetDateTime receivedAt, OffsetDateTime expiresAt,
                                   @DiagnosticHidden URI downloadUri, String billFileCode, String requestSha256,
                                   String evidenceSha256,
                                   @DiagnosticHidden byte[] evidence) {
    public AlipayStatementGrant {
        if (bindingRevisionId == null || bindingRevisionId.isBlank() || bindingRevisionId.length() > 64 || !hash(accountScopeHash)
                || billDate == null || requestedAt == null || receivedAt == null || expiresAt == null || receivedAt.isBefore(requestedAt)
                || !expiresAt.equals(requestedAt.plusSeconds(30)) || !expiresAt.isAfter(receivedAt)
                || downloadUri == null || downloadUri.getScheme() == null || !java.util.Set.of("http", "https").contains(downloadUri.getScheme()) || downloadUri.getHost() == null
                || downloadUri.getRawUserInfo() != null || downloadUri.getRawFragment() != null || downloadUri.toASCIIString().length() > 2048
                || billFileCode != null && !"EMPTY_DATA_WITH_BILL_FILE".equals(billFileCode)
                || !hash(requestSha256) || !hash(evidenceSha256) || evidence == null || evidence.length == 0 || evidence.length > 32768) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        evidence = evidence.clone();
        if (!evidenceSha256.equals(io.github.loadup.commons.util.DigestUtils.sha256(evidence))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
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
