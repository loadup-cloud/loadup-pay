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
package io.github.loadup.pay.realtime.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.app.port.TradeProjectionCodec;
import io.github.loadup.pay.realtime.app.security.RealtimeActor;
import io.github.loadup.pay.realtime.domain.gateway.ProjectionReceiptGateway;
import io.github.loadup.pay.realtime.domain.gateway.TradeProjectionGateway;
import io.github.loadup.pay.realtime.domain.model.ProjectionAdvance;
import io.github.loadup.pay.realtime.domain.model.ProjectionReceipt;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.QUERY_FORBIDDEN;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SOURCE_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
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

class TradeProjectionServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TradeProjectionConverter.class.getPackageName());
    final TradeProjectionGateway projections = mock(TradeProjectionGateway.class);
    final ProjectionReceiptGateway receipts = mock(ProjectionReceiptGateway.class);
    final TradeProjectionCodec codec = mock(TradeProjectionCodec.class);
    final AtomicReference<RealtimeActor> actor = new AtomicReference<>(new RealtimeActor("tenant", "payment", null,
            Set.of("pay:realtime:projection:payment")));
    final Map<String, ProjectionReceipt> accepted = new HashMap<>();
    final RealtimeFixtures.Transactions manager = new RealtimeFixtures.Transactions();
    final TradeProjectionService service = new TradeProjectionService(actor::get, projections, receipts, codec,
            context.getBean(TradeProjectionConverter.class), new RealtimeTransactions(manager), () -> "receipt",
            RealtimeFixtures.clock(), "acquire", "payment");

    @BeforeEach
    void setup() {
        when(codec.encode(any(TradePaymentProjection.class))).thenReturn("canonical");
        when(codec.hash(anyString())).thenReturn("a".repeat(64));
        when(codec.requestDigest(anyString(), anyString(), any(), anyString())).thenAnswer(c ->
                "%064x".formatted(Integer.toUnsignedLong(Objects.hash(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3)))));
        when(receipts.find(eq("tenant"), eq("payment"), anyString())).thenAnswer(c -> Optional.ofNullable(accepted.get(c.getArgument(2))));
        when(projections.applyPaymentIfNewer(eq("tenant"), any(), anyString())).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            TradePaymentProjection value = c.getArgument(1);
            return new ProjectionAdvance("APPLIED", value.source().number());
        });
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            ProjectionReceipt receipt = c.getArgument(1);
            accepted.put(receipt.deliveryId(), receipt);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void paymentSnapshotAndAckCommitWithoutReadingTheSourceServiceOrAParentOrder() {
        var ack = service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED));
        assertTrue(ack.accepted());
        assertEquals("APPLIED", ack.disposition());
        assertEquals(2, ack.projectedVersion());
        assertEquals(1, manager.commits);
        verify(projections, never()).findOrder(anyString(), anyString());
    }

    @Test
    void sameDeliveryReplaysTheOriginalAckWithoutApplyingAgain() {
        var event = RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED);
        var ack = service.applyPayment(event);
        assertEquals(ack, service.applyPayment(event));
        verify(projections, times(1)).applyPaymentIfNewer(anyString(), any(), anyString());
    }

    @Test
    void sameDeliveryWithChangedSnapshotIsRejectedEvenWhenSourceVersionIsPlausible() {
        service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> service.applyPayment(RealtimeFixtures.payment("delivery", 3, PaymentState.SUCCEEDED))).getResultCode());
    }

    @Test
    void olderSourceVersionIsDurablyAcknowledgedWithoutRollingBackTheProjection() {
        when(projections.applyPaymentIfNewer(eq("tenant"), any(), anyString())).thenReturn(new ProjectionAdvance("STALE", 5));
        var ack = service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.PROCESSING));
        assertTrue(ack.accepted());
        assertEquals(2, ack.sourceVersion());
        assertEquals(5, ack.projectedVersion());
        assertEquals("STALE", ack.disposition());
    }

    @Test
    void sameVersionDifferentFactsCannotProduceAnAck() {
        doThrow(new CommonException(SOURCE_CONFLICT)).when(projections).applyPaymentIfNewer(eq("tenant"), any(), anyString());
        assertThrows(CommonException.class, () -> service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED)));
        verify(receipts, never()).insert(anyString(), any());
    }

    @Test
    void eventReceiptFailureRollsBackTheApplicationTransaction() {
        doThrow(new CommonException(SNAPSHOT_INVALID)).when(receipts).insert(eq("tenant"), any());
        assertThrows(CommonException.class, () -> service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED)));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void merchantAndWrongServiceCannotPublishFinancialReadModels() {
        actor.set(new RealtimeActor("tenant", null, "merchant", Set.of("pay:realtime:projection:payment")));
        assertSame(QUERY_FORBIDDEN, assertThrows(CommonException.class,
                () -> service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED))).getResultCode());
        actor.set(new RealtimeActor("tenant", "acquire", null, Set.of("pay:realtime:projection:payment")));
        assertThrows(CommonException.class, () -> service.applyPayment(RealtimeFixtures.payment("delivery", 2, PaymentState.SUCCEEDED)));
        verifyNoInteractions(projections, receipts);
    }
}
