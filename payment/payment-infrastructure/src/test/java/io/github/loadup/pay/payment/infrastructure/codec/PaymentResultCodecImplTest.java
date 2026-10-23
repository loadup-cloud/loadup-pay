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

import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.PaymentObservation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PaymentResultCodecImplTest {
    final PaymentResultCodecImpl codec = new PaymentResultCodecImpl(JsonMapper.builder().build());
    final ChannelPaymentIdentity identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "binding", "a".repeat(64), "request");
    final LocalDateTime at = LocalDateTime.of(2026, 10, 22, 12, 0);

    PaymentObservation success(String transaction, long amount, LocalDateTime paidAt) {
        return new PaymentObservation(identity, ChannelPaymentOutcome.Kind.SUCCEEDED, DomainChecks.amount(amount, "CNY"),
                transaction, paidAt, PaymentObservation.Source.CALLBACK, "notification", "b".repeat(64));
    }

    @Test
    void transportEnvelopeChangesDoNotAlterCanonicalFinancialFacts() {
        var value = success("transaction", 1000, at);
        var query = new PaymentObservation(value.identity(), value.kind(), value.amount(), value.channelTransactionNo(), value.succeededAt(),
                PaymentObservation.Source.QUERY_RESPONSE, "query-1", "c".repeat(64));
        assertEquals(codec.factsDigest(value), codec.factsDigest(query));
    }

    @Test
    void amountTransactionAndPaidTimeAreFinancialFacts() {
        String original = codec.factsDigest(success("transaction", 1000, at));
        assertNotEquals(original, codec.factsDigest(success("transaction", 999, at)));
        assertNotEquals(original, codec.factsDigest(success("another-transaction", 1000, at)));
        assertNotEquals(original, codec.factsDigest(success("transaction", 1000, at.plusSeconds(1))));
    }

    @Test
    void actualAccountAndStableRequestIdentityAreProtected() {
        var value = success("transaction", 1000, at);
        var different = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "binding", "c".repeat(64), "request");
        var changed = new PaymentObservation(different, value.kind(), value.amount(), value.channelTransactionNo(), value.succeededAt(),
                value.source(), value.receiptKey(), value.sourceDigest());
        assertNotEquals(codec.factsDigest(value), codec.factsDigest(changed));
    }

    @Test
    void negativeAndIntermediateKindsDoNotShareTheSameFinancialDigest() {
        var unknown = new PaymentObservation(identity, ChannelPaymentOutcome.Kind.UNKNOWN, DomainChecks.amount(1000, "CNY"),
                null, null, PaymentObservation.Source.QUERY_RESPONSE, "query-1", "b".repeat(64));
        var noCollection = new PaymentObservation(identity, ChannelPaymentOutcome.Kind.NO_COLLECTION, unknown.amount(),
                null, null, unknown.source(), unknown.receiptKey(), unknown.sourceDigest());
        assertNotEquals(codec.factsDigest(unknown), codec.factsDigest(noCollection));
    }

    @Test
    void channelTimestampIsNormalizedToDatabaseMicroseconds() {
        var nanos = success("transaction", 1000, at.plusNanos(123456789));
        var micros = success("transaction", 1000, at.plusNanos(123456000));
        assertEquals(codec.factsDigest(nanos), codec.factsDigest(micros));
    }
}
