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
package io.github.loadup.pay.payment.infrastructure.codec;

import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundResultCodecImplTest {
    final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentAcceptanceCodec hashes = mock(PaymentAcceptanceCodec.class);
    final RefundResultCodecImpl codec = new RefundResultCodecImpl(json, hashes);

    @BeforeEach
    void setup() {
        when(hashes.checksum(anyString())).thenAnswer(c -> HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(((String) c.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
    }

    RefundObservation observation(String originalTransaction, String refundTransaction, long amount, String key) {
        return new RefundObservation("refund", new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "revision", "a".repeat(64), "payment-request"),
                "refund-request", originalTransaction, ChannelRefundOutcome.Kind.SUCCEEDED, DomainChecks.cnyAmount(amount, "CNY"), refundTransaction,
                LocalDateTime.of(2026, 10, 23, 12, 0, 0, 123456789), RefundObservation.Source.CALLBACK, key, "b".repeat(64), null);
    }

    @Test
    void financialDigestBindsOriginalAndRefundTransactionsAndExactLongAmount() {
        var original = observation("original-transaction", "refund-transaction", 9007199254740993L, "event");
        var digest = codec.factsDigest(original);
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertNotEquals(digest, codec.factsDigest(observation("other-original", "refund-transaction", 9007199254740993L, "event")));
        assertNotEquals(digest, codec.factsDigest(observation("original-transaction", "other-refund", 9007199254740993L, "event")));
        assertNotEquals(digest, codec.factsDigest(observation("original-transaction", "refund-transaction", 9007199254740993L - 1, "event")));
        assertEquals(digest, codec.factsDigest(observation("original-transaction", "refund-transaction", 9007199254740993L, "other-delivery")));
        assertEquals(123456000, original.succeededAt().getNano());
    }

    @Test
    void retainedNormalizedPayloadRoundTripsWithoutAmountPrecisionLoss() {
        var original = observation("original-transaction", "refund-transaction", 9007199254740993L, "event");
        assertEquals(original, json.readValue(codec.encode(original), RefundObservation.class));
    }

    @Test
    void missingRefundTransactionOrSuccessTimeCannotBeVerifiedSuccess() {
        var original = observation("original-transaction", "refund-transaction", 1000, "event");
        assertThrows(RuntimeException.class, () -> new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(),
                original.originalChannelTransactionNo(), original.kind(), original.amount(), null, original.succeededAt(),
                original.source(), original.receiptKey(), original.sourceDigest(), null));
        assertThrows(RuntimeException.class, () -> new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(),
                original.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.UNKNOWN, original.amount(), original.channelRefundTransactionNo(),
                original.succeededAt(), original.source(), original.receiptKey(), original.sourceDigest(), null));
    }

    @Test
    void acceptanceMetadataIsMicrosecondExactAndBoundIntoTheRetainedFactsDigest() {
        var original = observation("original-transaction", "refund-transaction", 1000, "event");
        var time = original.succeededAt().minusMinutes(1).withNano(987654321);
        var accepted = new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(), original.originalChannelTransactionNo(),
                original.kind(), original.amount(), original.channelRefundTransactionNo(), original.succeededAt(), original.source(), original.receiptKey(), original.sourceDigest(), time);
        assertEquals(987654000, accepted.acceptedAt().getNano());
        assertNotEquals(codec.factsDigest(original), codec.factsDigest(accepted));
        assertEquals(accepted, json.readValue(codec.encode(accepted), RefundObservation.class));
        assertThrows(RuntimeException.class, () -> new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(), original.originalChannelTransactionNo(),
                original.kind(), original.amount(), original.channelRefundTransactionNo(), original.succeededAt(), original.source(), original.receiptKey(), original.sourceDigest(), original.succeededAt().plusSeconds(1)));
    }

}
