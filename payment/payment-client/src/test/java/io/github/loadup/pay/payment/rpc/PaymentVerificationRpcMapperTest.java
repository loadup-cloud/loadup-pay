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
package io.github.loadup.pay.payment.rpc;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationOutcome;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentVerificationRpcMapperTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(PaymentVerificationRpcMapper.class.getPackageName());
    final PaymentVerificationRpcMapper mapper = spring.getBean(PaymentVerificationRpcMapper.class);
    final PaymentVerificationQuery query = new PaymentVerificationQuery("request", Channel.WECHAT, "a".repeat(64), PaymentLedgerKind.PAYMENT, "original-request", "statement-transaction", null);
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-28T14:00:00.123456+08:00");

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void queryPreservesOriginalScopeAndRejectsMissingUnknownOrRefundPresenceChanges() throws Exception {
        var wire = mapper.toWire(query);
        assertEquals(query, mapper.fromWire(WirePaymentVerificationQuery.parseFrom(wire.toByteArray())));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearSourceRequestId().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setChannel("UNKNOWN_VENDOR").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setKind("UNKNOWN_KIND").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setKind("REFUND").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setChannelRefundNo("refund").build()));
    }

    @Test
    void absentCoverageHasNoImplicitZeroMoneyVersionOrFinancialOutcome() {
        var value = new PaymentVerificationDTO(query, PaymentVerificationDisposition.NO_LOCAL_TARGET, null, null, null, null, null, null, null, null, null, null, null, null, at, at, null);
        var wire = mapper.toWire(value);
        assertFalse(wire.hasAmountMinor());
        assertFalse(wire.hasCurrency());
        assertFalse(wire.hasSourceVersion());
        assertFalse(wire.hasOutcome());
        assertEquals(PaymentVerificationDisposition.NO_LOCAL_TARGET, mapper.fromWire(wire).disposition());
        assertNull(mapper.fromWire(wire).amount());
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setSourceVersion(0L).build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setOutcome("NO_COLLECTION").build()));
    }

    @Test
    void refundAcceptanceTimestampIsPreservedWithoutBecomingTerminalEvidence() {
        var refund = new PaymentVerificationQuery("request", Channel.WECHAT, "a".repeat(64), PaymentLedgerKind.REFUND, "original-request", "statement-transaction", "refund-request");
        var value = new PaymentVerificationDTO(refund, PaymentVerificationDisposition.OBSERVED, "revision", "payment", "attempt", "refund", 2L, "PROCESSING",
                PaymentVerificationOutcome.UNKNOWN, Money.ofMinor(30L, "CNY"), "actual-transaction", null, null, "b".repeat(64), at, at, at.minusDays(1));
        var wire = mapper.toWire(value);
        assertTrue(wire.hasAcceptedAt());
        assertFalse(wire.hasSucceededAt());
        var restored = mapper.fromWire(wire);
        assertEquals(value.acceptedAt().toInstant(), restored.acceptedAt().toInstant());
        assertEquals(PaymentVerificationOutcome.UNKNOWN, restored.outcome());
    }

    @Test
    void observedMoneyRetainsInt64AndOriginalTransactionDifferencesWithoutLosingTimestampPresence() throws Exception {
        long exact = 9_007_199_254_740_993L;
        var value = new PaymentVerificationDTO(query, PaymentVerificationDisposition.OBSERVED, "revision", "payment", "attempt", null, 0L, "SUCCEEDED",
                PaymentVerificationOutcome.SUCCEEDED, Money.ofMinor(exact, "CNY"), "actual-transaction", null, at.minusMinutes(1), "b".repeat(64), at, at.plusSeconds(1), null);
        var wire = mapper.toWire(value);
        var restored = mapper.fromWire(WirePaymentVerificationDTO.parseFrom(wire.toByteArray()));
        assertEquals(exact, wire.getAmountMinor());
        assertEquals(exact, restored.amount().getCent());
        assertEquals(at.toInstant(), restored.startedAt().toInstant());
        assertEquals(0L, restored.sourceVersion());
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearSucceededAt().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().clearCurrency().build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setOutcome("UNKNOWN_ENUM").build()));
        assertThrows(CommonException.class, () -> mapper.fromWire(wire.toBuilder().setFinishedAt(wire.getStartedAt().toBuilder().setNanos(123456001)).build()));
    }
}
