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
package io.github.loadup.pay.payment.client.dto;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

/**
 * Private service-to-service source artifact. No browser action, file URL or financial result is implied.
 */
public record PaymentStatementDTO(String bindingRevisionId, Channel channel, String accountScopeHash,
                                  LocalDate billDate,
                                  String statementProfile, String format, String requestSha256, String evidenceSha256,
                                  String officialHashType,
                                  String officialHash, String contentSha256, OffsetDateTime receivedAt,
                                  OffsetDateTime downloadedAt,
                                  @DiagnosticHidden byte[] evidence, @DiagnosticHidden byte[] content) {
    public PaymentStatementDTO {
        if (!text(bindingRevisionId, 64) || channel == null || !hash(accountScopeHash) || billDate == null
                || billDate.getYear() < 1970 || billDate.getYear() > 9998 || !text(statementProfile, 64) || !text(format, 64)
                || !hash(requestSha256) || !hash(evidenceSha256) || !hash(contentSha256) || !text(officialHashType, 16)
                || officialHash == null || !officialHash.matches("[0-9a-f]{1,128}")
                || receivedAt == null || downloadedAt == null || downloadedAt.isBefore(receivedAt)
                || receivedAt.getNano() % 1000 != 0 || downloadedAt.getNano() % 1000 != 0
                || evidence == null || evidence.length == 0 || evidence.length > 32768
                || content == null || content.length == 0 || content.length > 16777216) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        evidence = evidence.clone();
        content = content.clone();
        if (!contentSha256.equals(digest("SHA-256", content)) || !evidenceSha256.equals(digest("SHA-256", evidence))
                || "SHA1".equals(officialHashType) && !officialHash.equals(digest("SHA-1", content))) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String digest(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    @Override
    public byte[] evidence() {
        return evidence.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
