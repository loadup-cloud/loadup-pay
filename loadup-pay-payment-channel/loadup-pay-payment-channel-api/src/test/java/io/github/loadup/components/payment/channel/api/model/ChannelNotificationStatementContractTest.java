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
package io.github.loadup.components.payment.channel.api.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.components.payment.channel.contract.Channel;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelNotificationStatementContractTest {
    @Test
    void notificationAcknowledgementIsBoundedAndProtocolOwned() {
        var ack = new ChannelNotificationAcknowledgement(200, "text/plain", "success");
        assertEquals(200, ack.httpStatus());
        assertThrows(RuntimeException.class, () -> new ChannelNotificationAcknowledgement(199, "text/plain", "x"));
        assertThrows(RuntimeException.class, () -> new ChannelNotificationAcknowledgement(600, "text/plain", "x"));
        assertThrows(RuntimeException.class, () -> new ChannelNotificationAcknowledgement(200, " ", "x"));
        assertThrows(
                RuntimeException.class, () -> new ChannelNotificationAcknowledgement(200, "text/\u0000plain", "x"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationAcknowledgement(200, "text/plain", "x".repeat(4097)));
    }

    @Test
    void notificationHintsAcceptOnlyBoundedLocators() {
        assertEquals("request-1", new ChannelNotificationHint("request-1").channelRequestNo());
        assertThrows(RuntimeException.class, () -> new ChannelNotificationHint(" "));
        assertEquals("refund-1", new ChannelRefundNotificationHint("request-1", "refund-1").channelRefundNo());
        assertThrows(RuntimeException.class, () -> new ChannelRefundNotificationHint(" ", "refund-1"));
        assertThrows(RuntimeException.class, () -> new ChannelRefundNotificationHint("request-1", " "));
    }

    @Test
    void notificationInputNormalizesHeadersAndDefensivelyCopiesBody() {
        var body = new byte[] {1, 2, 3};
        var input = new ChannelNotificationInput(
                "application/json", Map.of("Content-Type", List.of("application/json")), body);
        assertEquals(List.of("application/json"), input.headers().get("content-type"));
        body[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, input.body());
        input.body()[0] = 8;
        assertArrayEquals(new byte[] {1, 2, 3}, input.body());
    }

    @Test
    void notificationInputRejectsOutOfBoundsContentTypeBodyHeadersAndValues() {
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput(" ", Map.of("A", List.of("v")), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("x".repeat(257), Map.of("A", List.of("v")), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A", List.of("v")), null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A", List.of("v")), new byte[0]));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A", List.of("v")), new byte[65537]));
        assertThrows(RuntimeException.class, () -> new ChannelNotificationInput("text/plain", null, new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("", List.of("v")), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A B", List.of("v")), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A".repeat(65), List.of("v")), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A", List.of()), new byte[] {1}));
        assertThrows(
                RuntimeException.class, () -> new ChannelNotificationInput("text/plain", headers(65), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput(
                        "text/plain", Map.of("A", List.of("v", "v", "v", "v", "v", "v", "v", "v", "v")), new byte[] {1
                        }));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput(
                        "text/plain", Map.of("A", Arrays.asList("v", null)), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput(
                        "text/plain", Map.of("A", List.of("v".repeat(2049))), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", Map.of("A", List.of("v\u0000")), new byte[] {1}));
        var duplicateCase = new HashMap<String, List<String>>();
        duplicateCase.put("A", List.of("v"));
        duplicateCase.put("a", List.of("v"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelNotificationInput("text/plain", duplicateCase, new byte[] {1}));
        var tooLong = new HashMap<String, List<String>>();
        tooLong.put("A", Collections.nCopies(8, "a".repeat(2048)));
        tooLong.put("B", List.of("b"));
        assertThrows(RuntimeException.class, () -> new ChannelNotificationInput("text/plain", tooLong, new byte[] {1}));
    }

    @Test
    void statementScopeIsAccountBoundAndDateBounded() {
        assertEquals(Channel.ALIPAY, ApiFixtures.statementScope().channel());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(null, ApiFixtures.HASH, LocalDate.of(2026, 10, 23), "profile"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(Channel.ALIPAY, null, LocalDate.of(2026, 10, 23), "profile"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(Channel.ALIPAY, "bad", LocalDate.of(2026, 10, 23), "profile"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(Channel.ALIPAY, ApiFixtures.HASH, null, "profile"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(
                        Channel.ALIPAY, ApiFixtures.HASH, LocalDate.of(1969, 12, 31), "profile"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(Channel.ALIPAY, ApiFixtures.HASH, LocalDate.of(9999, 1, 1), "profile"));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(Channel.ALIPAY, ApiFixtures.HASH, LocalDate.of(2026, 10, 23), " "));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(
                        Channel.ALIPAY, ApiFixtures.HASH, LocalDate.of(2026, 10, 23), "p".repeat(65)));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementScope(
                        Channel.ALIPAY, ApiFixtures.HASH, LocalDate.of(2026, 10, 23), "p\u0000"));
    }

    @Test
    void statementRequestAndReceiptEnforceTransportProvenance() {
        assertEquals(1024, new ChannelStatementRequest(ApiFixtures.statementScope(), 1024).maxBytes());
        assertThrows(RuntimeException.class, () -> new ChannelStatementRequest(null, 1024));
        assertThrows(RuntimeException.class, () -> new ChannelStatementRequest(ApiFixtures.statementScope(), 0));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementRequest(ApiFixtures.statementScope(), ChannelStatementRequest.MAX_BYTES + 1));
        var receipt = ApiFixtures.statementReceipt();
        assertEquals(1, receipt.evidence().length);
        receipt.evidence()[0] = 9;
        assertEquals(1, receipt.evidence()[0]);
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        " ",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        "bad",
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        "bad",
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        null,
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        null,
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "z",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        "bad",
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        null,
                        ApiFixtures.EXPIRY,
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        null,
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.minusMinutes(1),
                        new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[0]));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelStatementReceipt(
                        "csv",
                        ApiFixtures.HASH,
                        ApiFixtures.HASH,
                        "SHA1",
                        "a",
                        ApiFixtures.HASH,
                        ApiFixtures.EXPIRY,
                        ApiFixtures.EXPIRY.plusMinutes(1),
                        new byte[32769]));
    }

    @Test
    void verifiedStatementCopiesContentAndRequiresScopeAndReceipt() {
        var content = new byte[] {1, 2, 3};
        var statement = new VerifiedChannelStatement(
                "revision-1", ApiFixtures.statementScope(), ApiFixtures.statementReceipt(), content);
        assertEquals(3, statement.size());
        content[0] = 9;
        assertEquals(1, statement.content()[0]);
        statement.content()[0] = 8;
        assertEquals(1, statement.content()[0]);
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelStatement(
                        " ", ApiFixtures.statementScope(), ApiFixtures.statementReceipt(), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelStatement("revision-1", null, ApiFixtures.statementReceipt(), new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelStatement("revision-1", ApiFixtures.statementScope(), null, new byte[] {1}));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelStatement(
                        "revision-1", ApiFixtures.statementScope(), ApiFixtures.statementReceipt(), null));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelStatement(
                        "revision-1", ApiFixtures.statementScope(), ApiFixtures.statementReceipt(), new byte[0]));
        assertThrows(
                RuntimeException.class,
                () -> new VerifiedChannelStatement(
                        "revision-1",
                        ApiFixtures.statementScope(),
                        ApiFixtures.statementReceipt(),
                        new byte[ChannelStatementRequest.MAX_BYTES + 1]));
    }

    private static Map<String, List<String>> headers(int count) {
        var headers = new HashMap<String, List<String>>();
        for (int i = 0; i < count; i++) {
            headers.put("X-" + i, List.of("v"));
        }
        return headers;
    }
}
