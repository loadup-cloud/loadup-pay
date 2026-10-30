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
package io.github.loadup.pay.reconciliation.domain.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Versioned business evidence framing, independent of public DTO JSON and database row serialization.
 */
final class ReconciliationFingerprint {
    private final MessageDigest digest;

    ReconciliationFingerprint(String format) {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
        field(format);
    }

    void field(String value) {
        byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) {
            digest.update(bytes);
        }
    }

    void scope(ReconciliationScope scope) {
        field(scope.channel().name());
        field(scope.accountScopeHash());
        field(scope.billDate().toString());
        field(scope.statementProfile());
    }

    void line(ReconciliationLine line) {
        field(line == null ? null : "line");
        if (line == null) {
            return;
        }
        field(line.key().kind().name());
        field(line.key().channelTransactionNo());
        field(line.key().channelRefundNo());
        field(line.merchantPaymentNo());
        field(Long.toString(line.amount().getCent()));
        field(line.amount().getCurrencyValue());
        field(line.status().name());
        field(Long.toString(line.recordNumber()));
    }

    String finish() {
        return HexFormat.of().formatHex(digest.digest());
    }
}
