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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.app.converter.MerchantNotificationConverter;
import io.github.loadup.pay.acquire.app.model.OrderSnapshotFacts;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationKey;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.acquire.domain.model.VerifiedPaymentOutcome;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.domain.model.VerifiedPaymentOutcome.Kind.SUCCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantNotificationEventsTest {
    private static final String TENANT = "tenant";
    private static final String ORDER_ID = "order";
    private static final String MERCHANT_ID = "merchant";
    private static final String TARGET = "merchant-notifications";

    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(MerchantNotificationConverter.class.getPackageName());
    private final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    private final OrderCreationCodec admissions = mock(OrderCreationCodec.class);
    private final OrderSnapshotReader factsReader = mock(OrderSnapshotReader.class);
    private final MerchantNotificationGateway deliveries = mock(MerchantNotificationGateway.class);
    private final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final UnitTransactions manager = new UnitTransactions();
    private final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    private final AtomicInteger idSequence = new AtomicInteger();
    private final AtomicReference<OrderAdmissionDTO> admission = new AtomicReference<>(CheckoutFixtures.admission());
    private final AtomicReference<StoredAcquireOrder> storedOrder = new AtomicReference<>();
    private final AtomicReference<OrderSnapshotFacts> currentFacts = new AtomicReference<>();
    private final Map<String, MerchantNotificationDelivery> existing = new HashMap<>();
    private final MerchantNotificationEvents events = new MerchantNotificationEvents(orders, admissions, factsReader,
            context.getBean(MerchantNotificationConverter.class), deliveries, tasks, ids);

    @BeforeEach
    void setup() {
        prepare(AcquireOrderState.PAID, 10000, NOW, 7);
        when(orders.lockOrder(TENANT, ORDER_ID)).thenAnswer(call -> {
            activeTenant();
            return storedOrder.get();
        });
        when(admissions.decodeAdmission(anyString(), anyString())).thenAnswer(call -> admission.get());
        when(factsReader.lockCurrent(TENANT, ORDER_ID)).thenAnswer(call -> {
            activeTenant();
            return currentFacts.get();
        });
        when(deliveries.findEvent(eq(TENANT), anyString(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(existing.get(key(call.getArgument(1), call.getArgument(2)))));
        doAnswer(call -> {
            activeTenant();
            MerchantNotificationDelivery delivery = call.getArgument(1);
            existing.put(key(delivery.eventId(), delivery.policy().targetRef()), delivery);
            return null;
        }).when(deliveries).insert(eq(TENANT), any());
        doAnswer(call -> {
            activeTenant();
            return null;
        }).when(tasks).register(anyString(), anyString(), any());
        when(ids.generate()).thenAnswer(call -> "delivery-" + idSequence.incrementAndGet());
    }

    @AfterEach
    void close() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        context.close();
    }

    @Test
    void disabledOriginalContractDoesNotCreateDeliveryOrTask() {
        write(() -> events.order(TENANT, currentFacts.get()));

        verify(deliveries, never()).insert(eq(TENANT), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
        verifyNoInteractions(ids);
    }

    @Test
    void paidOrderEmitsFrozenFullPaymentEventWithStableSchemaAndIdentity() {
        enableNotifications(TARGET, "signing-v1");

        write(() -> events.order(TENANT, currentFacts.get()));

        var delivery = onlyDelivery();
        Map<?, ?> body = json(delivery.body());
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertEquals("OrderFullyPaid", body.get("eventType"));
        assertEquals(1, ((Number) body.get("schemaVersion")).intValue());
        assertEquals("acquire", body.get("producer"));
        assertEquals("AcquireOrder", body.get("aggregateType"));
        assertEquals(ORDER_ID, body.get("aggregateId"));
        assertEquals(7, ((Number) body.get("aggregateVersion")).longValue());
        assertEquals(ORDER_ID, data.get("orderId"));
        assertEquals(10000, ((Number) data.get("orderPaidAmountMinor")).longValue());
        assertEquals(10000, ((Number) data.get("orderAmountMinor")).longValue());
        assertEquals("PAID", data.get("orderState"));
        assertEquals(DigestUtils.sha256(JsonUtil.toJson(List.of(
                "pay-merchant-event-v1", TENANT, "OrderFullyPaid", ORDER_ID, 7L))), delivery.eventId());
        assertEquals(delivery.eventId(), body.get("eventId"));
        assertEquals("delivery-1", delivery.id());
        verify(tasks).register(eq(MerchantNotificationService.TYPE), eq("delivery-1:0"), eq(delivery.nextAttemptAt()));
    }

    @Test
    void closedOrderEmitsEventWithItsPartialPaidAmount() {
        enableNotifications(TARGET, "signing-v1");
        prepare(AcquireOrderState.CLOSED, 4000, NOW, 8);

        write(() -> events.order(TENANT, currentFacts.get()));

        Map<?, ?> body = json(onlyDelivery().body());
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertEquals("OrderClosed", body.get("eventType"));
        assertEquals(4000, ((Number) data.get("orderPaidAmountMinor")).longValue());
        assertEquals(10000, ((Number) data.get("orderAmountMinor")).longValue());
        assertEquals("CLOSED", data.get("orderState"));
    }

    @Test
    void trustedSuccessfulPaymentEmitsPaymentScopedEventAndKeepsPartialOrderPending() {
        enableNotifications(TARGET, "signing-v1");
        prepare(AcquireOrderState.PENDING, 4000, NOW, 9);
        var payment = new VerifiedPaymentOutcome(ORDER_ID, "payment", DomainChecks.cnyAmount(4000, "CNY"),
                SUCCEEDED, 3, "evidence", false);

        write(() -> events.payment(TENANT, ORDER_ID, payment));

        MerchantNotificationDelivery delivery = onlyDelivery();
        Map<?, ?> body = json(delivery.body());
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertEquals("PaymentSucceeded", body.get("eventType"));
        assertEquals("PaymentOrder", body.get("aggregateType"));
        assertEquals("payment", body.get("aggregateId"));
        assertEquals(3, ((Number) body.get("aggregateVersion")).longValue());
        assertEquals(ORDER_ID, data.get("orderId"));
        assertEquals("payment", data.get("paymentId"));
        assertEquals(4000, ((Number) data.get("amountMinor")).longValue());
        assertEquals(4000, ((Number) data.get("orderPaidAmountMinor")).longValue());
        assertEquals(10000, ((Number) data.get("orderAmountMinor")).longValue());
        assertEquals("PENDING", data.get("orderState"));
        assertEquals(3, delivery.sourceVersion());
    }

    @Test
    void repeatedSameEventTargetBodyAndPolicyIsIdempotentWithoutAnotherTask() {
        enableNotifications(TARGET, "signing-v1");
        var header = currentFacts.get();

        write(() -> events.order(TENANT, header));
        write(() -> events.order(TENANT, header));

        verify(deliveries, times(1)).insert(eq(TENANT), any());
        verify(tasks, times(1)).register(anyString(), anyString(), any());
    }

    @Test
    void sameEventKeyRejectsChangedBodyOrPolicy() {
        enableNotifications(TARGET, "signing-v1");
        var initial = currentFacts.get();
        write(() -> events.order(TENANT, initial));

        var changedBody = new OrderSnapshotFacts(initial.orderId(), initial.payeeMerchantId(), initial.merchantOrderNo(),
                initial.amount(), initial.paidMinor(), initial.reservedMinor(), initial.state(), initial.orderCreatedAt(),
                initial.sourceUpdatedAt().plusSeconds(1), initial.sourceVersion());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> write(() -> events.order(TENANT, changedBody))).getResultCode());

        admission.set(withNotifications(TARGET, "signing-v2"));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> write(() -> events.order(TENANT, initial))).getResultCode());
        verify(deliveries, times(1)).insert(eq(TENANT), any());
        verify(tasks, times(1)).register(anyString(), anyString(), any());
    }

    @Test
    void foreignTenantAndMissingWritableTransactionAreRejectedBeforeStorageAccess() {
        enableNotifications(TARGET, "signing-v1");

        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> events.order(TENANT, currentFacts.get())).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                () -> transactions.write("other-tenant", () -> {
                    events.order(TENANT, currentFacts.get());
                    return true;
                })).getResultCode());

        verifyNoInteractions(orders, admissions, factsReader, deliveries, tasks, ids);
    }

    private void prepare(AcquireOrderState state, long paid, java.time.LocalDateTime updatedAt, long version) {
        var fixture = CheckoutFixtures.order(state, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
        var order = new AcquireOrder(ORDER_ID, MERCHANT_ID, DomainChecks.cnyAmount(10000, "CNY"), paid, 0,
                paid == 0 ? 0 : 1, 0, state, NOW.plusMinutes(10), UnpaidOrderAction.CLOSE,
                fixture.order().paymentPolicy(), 0, version);
        storedOrder.set(new StoredAcquireOrder(order, fixture.merchantOrderNo(), fixture.subject(), fixture.admission(),
                fixture.admittedAt(), fixture.callerScopeHash(), fixture.snapshotId(), fixture.admissionPayload(), fixture.payloadHash()));
        currentFacts.set(new OrderSnapshotFacts(ORDER_ID, MERCHANT_ID, fixture.merchantOrderNo(), order.amount(), paid, 0,
                state.name(), NOW.minusDays(1), updatedAt, version));
    }

    @Test
    void terminalRefundUsesOriginalPolicyAndSourceIdentityWithoutChangingOrderAmounts() {
        enableNotifications(TARGET, "signing-v1");
        var source = refundEvent("RefundOrder", "SUCCEEDED", 1000, 1000, 0);
        var original = storedOrder.get();
        var registered = transactions.write(TENANT, () -> events.refund(TENANT, source));
        var replay = transactions.write(TENANT, () -> events.refund(TENANT, source));

        assertEquals(registered, replay);
        assertEquals("ENQUEUED", registered.disposition());
        var delivery = onlyDelivery();
        var body = json(delivery.body());
        var data = (Map<?, ?>) body.get("data");
        assertEquals("payment", body.get("producer"));
        assertEquals("RefundSucceeded", body.get("eventType"));
        assertEquals("refund", body.get("aggregateId"));
        assertEquals("request", data.get("refundRequestId"));
        assertEquals("R001", data.get("merchantRefundNo"));
        assertEquals(1000, ((Number) data.get("succeededMinor")).longValue());
        assertEquals(source.occurredAt().toInstant(), io.github.loadup.commons.time.BusinessTime.external(delivery.occurredAt()).toInstant());
        assertEquals(original, storedOrder.get());
        verify(deliveries, times(1)).insert(eq(TENANT), any());
        verify(tasks, times(1)).register(anyString(), anyString(), any());
    }

    @Test
    void completedPartialRefundRequestKeepsFixedTotalAndDistinctEventType() {
        enableNotifications(TARGET, "signing-v1");
        var source = refundEvent("RefundRequest", "PARTIAL_SUCCEEDED", 1500, 1000, 500);
        transactions.write(TENANT, () -> events.refund(TENANT, source));
        var body = json(onlyDelivery().body());
        var data = (Map<?, ?>) body.get("data");
        assertEquals("RefundRequestCompleted", body.get("eventType"));
        assertEquals("request", body.get("aggregateId"));
        assertEquals("PARTIAL_SUCCEEDED", data.get("state"));
        assertEquals(1500, ((Number) data.get("amountMinor")).longValue());
        assertEquals(500, ((Number) data.get("failedMinor")).longValue());
    }

    @Test
    void disabledRefundPolicyReturnsStableDispositionWithoutATask() {
        var source = refundEvent("RefundOrder", "FAILED", 1000, 0, 1000);
        var registration = transactions.write(TENANT, () -> events.refund(TENANT, source));
        assertEquals("DISABLED", registration.disposition());
        assertEquals(64, registration.merchantEventId().length());
        verifyNoInteractions(deliveries, tasks, ids);
    }

    private io.github.loadup.commons.event.EventEnvelope<io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO> refundEvent(
            String aggregateType, String state, long amount, long succeeded, long failed) {
        boolean child = "RefundOrder".equals(aggregateType);
        var data = new io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO(aggregateType, child ? "refund" : null,
                "request", child ? "payment" : null, ORDER_ID, MERCHANT_ID, "R001", amount, "CNY", state, succeeded, failed, 3,
                child && "SUCCEEDED".equals(state) ? io.github.loadup.commons.time.BusinessTime.external(NOW.minusSeconds(1)) : null);
        return new io.github.loadup.commons.event.EventEnvelope<>("internal-delivery", "internal-business", "payment",
                io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO.TYPE, 1, data.aggregateId(), 3,
                io.github.loadup.commons.time.BusinessTime.external(NOW), data);
    }

    private void enableNotifications(String targetRef, String signingIdentityRef) {
        admission.set(withNotifications(targetRef, signingIdentityRef));
    }

    private static OrderAdmissionDTO withNotifications(String targetRef, String signingIdentityRef) {
        var original = CheckoutFixtures.admission();
        AcquiringTermsDTO terms = original.terms();
        var notification = new NotificationTermsDTO(true, targetRef, signingIdentityRef, 1000, 1,
                BigDecimal.valueOf(2), 60, 5, 3600);
        var enabledTerms = new AcquiringTermsDTO(terms.schemaVersion(), terms.order(), terms.payment(), terms.fee(),
                terms.refund(), notification, terms.limits(), terms.capabilities(), terms.routingProfileRef(), terms.returnTargetCode());
        return new OrderAdmissionDTO(original.admissionId(), original.orderId(), original.payeeMerchantId(),
                original.decision(), original.reasonCode(), original.requestDigest(), original.contractId(),
                original.revision(), original.generation(), original.snapshotHash(), original.compiledTermsHash(),
                original.admittedAt(), original.materializeBefore(), enabledTerms);
    }

    private void write(Runnable action) {
        transactions.write(TENANT, () -> {
            action.run();
            return true;
        });
    }

    private void activeTenant() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(TENANT, TenantUtil.getTenantId());
    }

    private MerchantNotificationDelivery onlyDelivery() {
        assertEquals(1, existing.size());
        return existing.values().iterator().next();
    }

    private static String key(String eventId, String targetRef) {
        return eventId + ':' + targetRef;
    }

    private static Map<?, ?> json(String value) {
        return JsonMapper.builder().build().readValue(value, Map.class);
    }
}
