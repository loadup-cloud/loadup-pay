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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.app.converter.RefundNotificationConverter;
import io.github.loadup.pay.acquire.app.model.MerchantNotificationRegistration;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundNotificationServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundNotificationConverter.class.getPackageName());
    final AtomicReference<AcquireServiceActor> actor = new AtomicReference<>(new AcquireServiceActor("tenant", "payment", Set.of("pay:acquire:refund:notification")));
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final PaymentOutcomeGateway payments = mock(PaymentOutcomeGateway.class);
    final RefundNotificationReceiptGateway receipts = mock(RefundNotificationReceiptGateway.class);
    final MerchantNotificationEvents notifications = mock(MerchantNotificationEvents.class);
    final UnitTransactions manager = new UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    final Map<String, RefundNotificationReceipt> accepted = new HashMap<>();
    final StoredAcquireOrder stored = order(AcquireOrderState.PAID, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
    final RefundNotificationService service = new RefundNotificationService(actor::get, orders, payments, receipts, notifications,
            context.getBean(RefundNotificationConverter.class), new OrderCreationTransactions(manager), () -> "receipt-" + ids.incrementAndGet(),
            clock(NOW), "payment");

    @BeforeEach void setup() {
        when(orders.lockOrder("tenant", "order")).thenReturn(stored);
        when(payments.lockPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation(AllocationState.CONSUMED)));
        when(receipts.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(accepted.get(c.<String>getArgument(1))));
        when(notifications.refund(eq("tenant"), any())).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return new MerchantNotificationRegistration("f".repeat(64), "ENQUEUED");
        });
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var receipt = c.<RefundNotificationReceipt>getArgument(1);
            accepted.put(receipt.deliveryId(), receipt);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
    }
    @AfterEach void close() { context.close(); }

    StoredPaymentAllocation allocation(AllocationState state) {
        return new StoredPaymentAllocation(new OrderPaymentAllocation("allocation", "order", "payment", "merchant:merchant", "action",
                "a".repeat(64), DomainChecks.cnyAmount(2000, "CNY"), state, 5), "b".repeat(64), "{}", "c".repeat(64), 0);
    }
    EventEnvelope<RefundMerchantEventDTO> child() {
        return event(new RefundMerchantEventDTO("RefundOrder", "refund", "request", "payment", "order", "merchant", "R001",
                1000, "CNY", "SUCCEEDED", 1000, 0, 3, BusinessTime.external(NOW.minusSeconds(1))));
    }
    EventEnvelope<RefundMerchantEventDTO> event(RefundMerchantEventDTO data) {
        return new EventEnvelope<>("delivery", "business", "payment", RefundMerchantEventDTO.TYPE, 1, data.aggregateId(), data.sourceVersion(),
                BusinessTime.external(NOW), data);
    }

    @Test void registrationAndReceiptShareOneTransactionAndReplayNeedsNoSourceAvailability() {
        var first = service.accept(child());
        assertEquals("ENQUEUED", first.disposition());
        assertEquals("business", first.businessEventId());
        assertEquals("refund", first.aggregateId());
        assertEquals(3, first.sourceVersion());
        when(orders.lockOrder(anyString(), anyString())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertEquals(first, service.accept(child()));
        verify(notifications, times(1)).refund(eq("tenant"), any());
        verify(receipts, times(1)).insert(eq("tenant"), any());
        verify(payments, never()).apply(anyString(), any(), any(), any(), anyLong());
        assertEquals(1, manager.commits);
    }

    @Test void changedSourceOnTheSameDeliveryConflictsWithoutAnotherRegistration() {
        var original = child();
        service.accept(original);
        var changed = new EventEnvelope<>(original.deliveryId(), original.businessEventId(), original.producer(), original.eventType(),
                original.schemaVersion(), original.aggregateId(), original.aggregateVersion(), original.occurredAt().plusSeconds(1), original.data());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.accept(changed)).getResultCode());
        verify(notifications, times(1)).refund(eq("tenant"), any());
    }

    @Test void latePaymentProjectionRetriesWithoutCreatingAReceiptOrNotification() {
        when(payments.lockPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation(AllocationState.HELD)));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> service.accept(child())).getResultCode());
        verifyNoInteractions(notifications);
        assertTrue(accepted.isEmpty());
        assertEquals(1, manager.rollbacks);
    }

    @Test void partialRequestCompletionDoesNotRequireAChildPaymentId() {
        var event = event(new RefundMerchantEventDTO("RefundRequest", null, "request", null, "order", "merchant", "R001",
                1500, "CNY", "PARTIAL_SUCCEEDED", 1000, 500, 5, null));
        assertEquals("request", service.accept(event).aggregateId());
        verifyNoInteractions(payments);
    }

    @Test void originalDisabledPolicyIsDurablyAcknowledged() {
        when(notifications.refund(eq("tenant"), any())).thenReturn(new MerchantNotificationRegistration("f".repeat(64), "DISABLED"));
        assertEquals("DISABLED", service.accept(child()).disposition());
        assertEquals("DISABLED", accepted.get("delivery").disposition());
    }

    @Test void foreignMerchantCannotRegisterAnEventForTheOriginalOrder() {
        var data = child().data();
        var foreign = event(new RefundMerchantEventDTO(data.aggregateType(), data.refundId(), data.refundRequestId(), data.paymentId(), data.orderId(),
                "other-merchant", data.merchantRefundNo(), data.amountMinor(), data.currency(), data.state(), data.succeededMinor(), data.failedMinor(),
                data.sourceVersion(), data.succeededAt()));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.accept(foreign)).getResultCode());
        verifyNoInteractions(notifications, payments);
        assertTrue(accepted.isEmpty());
    }

    @Test void configuredServiceAndDedicatedPermissionAreBothRequired() {
        actor.set(new AcquireServiceActor("tenant", "other", Set.of("pay:acquire:refund:notification")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.accept(child())).getResultCode());
        actor.set(new AcquireServiceActor("tenant", "payment", Set.of("pay:acquire:fee:refund")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.accept(child())).getResultCode());
        verifyNoInteractions(orders, receipts, payments, notifications);
    }

    @Test void registrationAndReceiptFailuresReturnNoAck() {
        when(notifications.refund(eq("tenant"), any())).thenThrow(new CommonException(CONCURRENT_UPDATE));
        assertThrows(CommonException.class, () -> service.accept(child()));
        assertTrue(accepted.isEmpty());
        verify(receipts, never()).insert(anyString(), any());
        assertEquals(1, manager.rollbacks);
        // The fake manager checks propagation; database rollback still requires MySQL acceptance.
    }
}
