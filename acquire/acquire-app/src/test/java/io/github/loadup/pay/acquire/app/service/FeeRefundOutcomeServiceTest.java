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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.FeeRefundConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.domain.gateway.FeeRefundReceiptGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderFeeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.FeeRefundReceipt;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.admission;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeeRefundOutcomeServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FeeRefundConverter.class.getPackageName());
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final OrderFeeGateway fees = mock(OrderFeeGateway.class);
    final FeeRefundReceiptGateway receipts = mock(FeeRefundReceiptGateway.class);
    final OrderCreationCodec codec = mock(OrderCreationCodec.class);
    final PaymentOutcomeClient payment = mock(PaymentOutcomeClient.class);
    final AtomicReference<AcquireServiceActor> actor = new AtomicReference<>(new AcquireServiceActor("tenant", "payment", Set.of("pay:acquire:fee:refund")));
    final Map<String, FeeRefundReceipt> accepted = new HashMap<>();
    final UnitTransactions manager = new UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    // Deliberately INIT: payment's refund fact may arrive before acquire has applied the payment outcome.
    final io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder stored = order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
    final FeeRefundOutcomeService service = new FeeRefundOutcomeService(actor::get, payment, orders, fees, receipts, codec,
            context.getBean(AcquireOrderConverter.class), context.getBean(FeeRefundConverter.class), new OrderCreationTransactions(manager),
            () -> "receipt-" + ids.incrementAndGet(), clock(NOW), "payment");

    OrderRefundSummaryEventDTO summary(long version, long refunded) {
        return new OrderRefundSummaryEventDTO("order", "merchant", "CNY", refunded, 0, version);
    }

    EventEnvelope<OrderRefundSummaryEventDTO> event(String delivery, long version, long refunded) {
        return new EventEnvelope<>(delivery, "business-" + delivery, "payment", FeeRefundOutcomeService.TYPE, 1,
                "order", version, BusinessTime.external(NOW), summary(version, refunded));
    }

    @BeforeEach
    void setup() {
        when(payment.supportsTenant("tenant")).thenReturn(true);
        when(payment.refundSummary("tenant", "order")).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return summary(3, 1000);
        });
        when(orders.findOrder("tenant", "order")).thenReturn(Optional.of(stored));
        when(orders.lockOrder("tenant", "order")).thenReturn(stored);
        when(codec.decodeAdmission(stored.admissionPayload(), stored.payloadHash())).thenReturn(admission());
        when(codec.checksum(anyString())).thenAnswer(c -> HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(c.<String>getArgument(0).getBytes(StandardCharsets.UTF_8))));
        when(receipts.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(accepted.get(c.<String>getArgument(1))));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var receipt = c.<FeeRefundReceipt>getArgument(1);
            accepted.put(receipt.deliveryId(), receipt);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(fees).applyRefundBasis(anyString(), any(), any(), anyLong(), anyLong(), anyString(), any());
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void earlyRefundBasisAndReceiptCommitTogetherAndReplayNeedsNoRemoteAvailability() {
        var first = service.accept(event("delivery", 3, 1000));
        assertTrue(first.accepted());
        assertEquals(3, first.appliedVersion());
        verify(fees).applyRefundBasis(eq("tenant"), eq(stored.order()), any(), eq(3L), eq(1000L), eq("business-delivery"), eq(NOW));
        when(payment.refundSummary(anyString(), anyString())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertEquals(first, service.accept(event("delivery", 3, 1000)));
        verify(payment, times(1)).refundSummary("tenant", "order");
        verify(receipts, times(1)).insert(eq("tenant"), any());
        assertEquals(1, manager.commits);
    }

    @Test
    void staleEventUsesVerifiedLatestCumulativeAndPreservesItsOwnSourceIdentity() {
        var ack = service.accept(event("old-delivery", 1, 0));
        assertEquals(1, ack.sourceVersion());
        assertEquals(3, ack.appliedVersion());
        assertEquals("business-old-delivery", ack.businessEventId());
        verify(fees).applyRefundBasis(eq("tenant"), eq(stored.order()), any(), eq(3L), eq(1000L), eq("business-old-delivery"), eq(NOW));
    }

    @Test
    void changedEnvelopeOnTheSameDeliveryIsAConflictBeforeCallingPaymentAgain() {
        var original = event("delivery", 3, 1000);
        service.accept(original);
        var changed = new EventEnvelope<>(original.deliveryId(), original.businessEventId(), original.producer(), original.eventType(),
                original.schemaVersion(), original.aggregateId(), original.aggregateVersion(), original.occurredAt().plusSeconds(1), original.data());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.accept(changed)).getResultCode());
        verify(payment, times(1)).refundSummary("tenant", "order");
    }

    @Test
    void equalVersionWithDifferentMoneyAndUnverifiedFutureVersionsCannotAlterTheFee() {
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.accept(event("changed", 3, 999))).getResultCode());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.accept(event("future", 4, 1000))).getResultCode());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.accept(event("decreasing", 2, 1100))).getResultCode());
        verifyNoInteractions(fees);
        verify(receipts, never()).insert(anyString(), any());
    }

    @Test
    void configuredServiceAndDedicatedPermissionAreBothRequired() {
        actor.set(new AcquireServiceActor("tenant", "other", Set.of("pay:acquire:fee:refund")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.accept(event("delivery", 3, 1000))).getResultCode());
        actor.set(new AcquireServiceActor("tenant", "payment", Set.of("pay:acquire:payment:outcome")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.accept(event("delivery", 3, 1000))).getResultCode());
        verifyNoInteractions(payment, fees, receipts, orders);
    }

    @Test
    void feeFailureRollsBackWithoutIssuingAReceiptOrAck() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(fees).applyRefundBasis(anyString(), any(), any(), anyLong(), anyLong(), anyString(), any());
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class, () -> service.accept(event("delivery", 3, 1000))).getResultCode());
        verify(receipts, never()).insert(anyString(), any());
        assertEquals(1, manager.rollbacks);
        assertTrue(accepted.isEmpty());
    }

    @Test
    void receiptFailureAlsoFailsTheTransactionAfterApplyingTheFee() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(receipts).insert(anyString(), any());
        assertThrows(CommonException.class, () -> service.accept(event("delivery", 3, 1000)));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }
}
