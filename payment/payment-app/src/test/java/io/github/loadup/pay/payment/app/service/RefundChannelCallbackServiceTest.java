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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.app.converter.RefundCallbackObservationConverter;
import io.github.loadup.pay.payment.app.model.ChannelCallbackEndpoint;
import io.github.loadup.pay.payment.app.model.RefundResultApplication;
import io.github.loadup.pay.payment.app.port.ChannelCallbackEndpointResolver;
import io.github.loadup.pay.payment.app.port.RefundResultCodec;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelNotificationGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundCallbackTargetGateway;
import io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundChannelCallbackServiceTest {
    private static final String ENDPOINT_REF = "refund-endpoint";
    private static final String TENANT = "tenant";
    private static final String FACTS_DIGEST = "c".repeat(64);
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(RefundCallbackObservationConverter.class.getPackageName());
    private final ChannelCallbackEndpointResolver endpoints = mock(ChannelCallbackEndpointResolver.class);
    private final RefundCallbackTargetGateway targets = mock(RefundCallbackTargetGateway.class);
    private final PaymentChannelNotificationGateway notifications = mock(PaymentChannelNotificationGateway.class);
    private final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
    private final RefundResultService results = mock(RefundResultService.class);
    private final RefundResultCodec facts = mock(RefundResultCodec.class);
    private final RefundCallbackObservationConverter converter = context.getBean(RefundCallbackObservationConverter.class);
    private final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());
    private final RefundChannelCallbackService service = new RefundChannelCallbackService(
            endpoints, targets, notifications, channels, results, facts, converter, transactions);
    private final ChannelNotificationInput input = new ChannelNotificationInput(
            "application/json", Map.of("wechatpay-signature", java.util.List.of("sig")), "{\"refund\":true}".getBytes());

    private PreparedChannelRefund target() {
        var child = RefundResultFixtures.initial().allocations().getFirst();
        return new PreparedChannelRefund(child.original(), child.refund().id(), child.refund().channelRefundNo(),
                child.refund().amount(), io.github.loadup.commons.money.Money.ofMinor(1000, "CNY"), child.channelTransactionNo());
    }

    private RefundObservation query() {
        return new RefundObservation(target().refundId(), target().original(), target().channelRefundNo(),
                target().originalChannelTransactionNo(), ChannelRefundOutcome.Kind.SUCCEEDED, target().amount(),
                "vendor-refund-ref", RefundResultFixtures.NOW, RefundObservation.Source.QUERY_RESPONSE,
                "query-key", "a".repeat(64));
    }

    private VerifiedChannelRefundNotification verified(String notificationId, String sourceDigest, String vendorRef) {
        return new VerifiedChannelRefundNotification(target(), notificationId, vendorRef, "REFUND_SUCCESS", sourceDigest);
    }

    private ChannelNotificationAcknowledgement acknowledgement(boolean success) {
        return new ChannelNotificationAcknowledgement(success ? 200 : 500, "text/plain", success ? "accepted" : "retry");
    }

    private void setup() {
        var endpoint = new ChannelCallbackEndpoint(TENANT, target().original().channel(), target().original().bindingRevisionId());
        when(endpoints.resolve(ENDPOINT_REF)).thenReturn(Optional.of(endpoint));
        when(targets.find(TENANT, endpoint.bindingRevisionId(), target().original().channelRequestNo(), target().channelRefundNo()))
                .thenReturn(Optional.of(target()));
        when(notifications.identifyRefund(endpoint.channel(), endpoint.bindingRevisionId(), input)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ChannelRefundNotificationHint(target().original().channelRequestNo(), target().channelRefundNo());
        });
        when(notifications.verifyRefund(target(), input)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return verified("notification-1", "b".repeat(64), "vendor-refund-ref");
        });
        when(channels.queryRefund(target())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return query();
        });
        when(facts.factsDigest(any())).thenReturn(FACTS_DIGEST);
        when(results.record(eq(TENANT), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new RefundResultApplication(RefundReceiptDisposition.APPLIED, "receipt", null);
        });
        when(notifications.acknowledgement(any(), anyBoolean())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return acknowledgement(call.getArgument(1));
        });
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void verifiesPreparedRefundThenQueriesAndRecordsCallbackFactsBeforeAck() {
        setup();
        var prepared = target();
        var endpoint = new ChannelCallbackEndpoint(TENANT, prepared.original().channel(), prepared.original().bindingRevisionId());

        var ack = TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input));

        assertEquals(acknowledgement(true), ack);
        var captured = org.mockito.ArgumentCaptor.forClass(RefundObservation.class);
        verify(results).record(eq(TENANT), captured.capture());
        assertEquals(RefundObservation.Source.CALLBACK, captured.getValue().source());
        assertEquals("refund-notification:" + io.github.loadup.commons.util.DigestUtils.sha256("14:notification-1" + FACTS_DIGEST),
                captured.getValue().receiptKey());
        var calls = inOrder(notifications, channels, results);
        verify(endpoints).resolve(ENDPOINT_REF);
        verify(targets).find(TENANT, endpoint.bindingRevisionId(), prepared.original().channelRequestNo(), prepared.channelRefundNo());
        calls.verify(notifications).identifyRefund(endpoint.channel(), endpoint.bindingRevisionId(), input);
        calls.verify(notifications).verifyRefund(prepared, input);
        calls.verify(channels).queryRefund(prepared);
        calls.verify(results).record(eq(TENANT), any(RefundObservation.class));
        calls.verify(notifications).acknowledgement(endpoint.channel(), true);
    }

    @Test
    void verificationFailureDoesNotQueryOrRecord() {
        setup();
        doThrow(new CommonException(PaymentResultCodeEnum.SNAPSHOT_INVALID)).when(notifications).verifyRefund(target(), input);

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));

        verify(channels, never()).queryRefund(any());
        verify(results, never()).record(anyString(), any());
        verify(notifications).acknowledgement(target().original().channel(), false);
    }

    @Test
    void mismatchedQueryAmountReturnsNegativeAck() {
        setup();
        var q = query();
        when(channels.queryRefund(target())).thenReturn(new RefundObservation(q.refundId(), q.original(), q.channelRefundNo(),
                q.originalChannelTransactionNo(), q.kind(), io.github.loadup.commons.money.Money.ofMinor(1, "CNY"),
                q.channelRefundTransactionNo(), q.succeededAt(), q.source(), q.receiptKey(), q.sourceDigest()));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        verify(results, never()).record(anyString(), any());
        verify(notifications).acknowledgement(target().original().channel(), false);
    }

    @Test
    void mismatchedQueryRefundIdAndOriginalTransactionReturnNegativeAck() {
        setup();
        var q = query();
        when(channels.queryRefund(target())).thenReturn(
                new RefundObservation("other-refund", q.original(), q.channelRefundNo(), q.originalChannelTransactionNo(),
                        q.kind(), q.amount(), q.channelRefundTransactionNo(), q.succeededAt(), q.source(), q.receiptKey(), q.sourceDigest()),
                new RefundObservation(q.refundId(), q.original(), q.channelRefundNo(), "other-original-transaction",
                        q.kind(), q.amount(), q.channelRefundTransactionNo(), q.succeededAt(), q.source(), q.receiptKey(), q.sourceDigest()));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));

        verify(results, never()).record(anyString(), any());
        verify(notifications, times(2)).acknowledgement(target().original().channel(), false);
    }

    @Test
    void lookupOriginalBindingMismatchStopsBeforeVerificationOrQuery() {
        setup();
        var original = target().original();
        var otherOriginal = new ChannelPaymentIdentity(original.paymentId(), original.attemptId(), original.channel(),
                "other-binding", original.accountScopeHash(), original.channelRequestNo());
        var wrongTarget = new PreparedChannelRefund(otherOriginal, target().refundId(), target().channelRefundNo(),
                target().amount(), target().originalAmount(), target().originalChannelTransactionNo());
        when(targets.find(anyString(), anyString(), anyString(), anyString())).thenReturn(Optional.of(
                wrongTarget));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        verify(notifications, never()).verifyRefund(any(), any());
        verify(channels, never()).queryRefund(any());
        verify(results, never()).record(anyString(), any());
    }

    @Test
    void verifiedTargetMismatchStopsBeforeChannelQuery() {
        setup();
        var original = target().original();
        var otherOriginal = new ChannelPaymentIdentity(original.paymentId(), "other-attempt", original.channel(),
                original.bindingRevisionId(), original.accountScopeHash(), original.channelRequestNo());
        var wrongTarget = new PreparedChannelRefund(otherOriginal, target().refundId(), target().channelRefundNo(),
                target().amount(), target().originalAmount(), target().originalChannelTransactionNo());
        when(notifications.verifyRefund(target(), input)).thenReturn(new VerifiedChannelRefundNotification(
                wrongTarget, "notification-1", "vendor-refund-ref", "REFUND_SUCCESS", "b".repeat(64)));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        verify(channels, never()).queryRefund(any());
        verify(results, never()).record(anyString(), any());
    }

    @Test
    void verifiedVendorRefundReferenceMismatchStopsAfterQueryAndBeforeRecord() {
        setup();
        when(notifications.verifyRefund(target(), input)).thenReturn(verified("notification-1", "b".repeat(64), "another-vendor-ref"));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));

        verify(channels).queryRefund(target());
        verify(results, never()).record(anyString(), any());
        verify(notifications).acknowledgement(target().original().channel(), false);
    }

    @Test
    void resultConflictOrFailureReturnsNegativeAck() {
        setup();
        when(results.record(eq(TENANT), any())).thenReturn(new RefundResultApplication(RefundReceiptDisposition.CONFLICT, "conflict", null));
        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        when(results.record(eq(TENANT), any())).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        verify(notifications, times(2)).acknowledgement(target().original().channel(), false);
    }

    @Test
    void unknownQueryCanBeRecordedAndAcknowledged() {
        setup();
        var q = new RefundObservation(target().refundId(), target().original(), target().channelRefundNo(),
                target().originalChannelTransactionNo(), ChannelRefundOutcome.Kind.UNKNOWN, target().amount(), null, null,
                RefundObservation.Source.QUERY_RESPONSE, "unknown-key", "a".repeat(64));
        when(channels.queryRefund(target())).thenReturn(q);

        assertEquals(acknowledgement(true), TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        verify(results).record(eq(TENANT), any(RefundObservation.class));
        verify(notifications).acknowledgement(target().original().channel(), true);
    }

    @Test
    void sameEventWithChangedFactsGetsDifferentReceiptKeyAndCanProgress() {
        setup();
        when(notifications.verifyRefund(target(), input)).thenReturn(
                verified("notification-1", "b".repeat(64), "vendor-refund-ref"),
                verified("notification-1", "d".repeat(64), "vendor-refund-ref"));
        var unknown = new RefundObservation(target().refundId(), target().original(), target().channelRefundNo(),
                target().originalChannelTransactionNo(), ChannelRefundOutcome.Kind.UNKNOWN, target().amount(), null, null,
                RefundObservation.Source.QUERY_RESPONSE, "unknown-key", "a".repeat(64));
        when(channels.queryRefund(target())).thenReturn(unknown, query());
        when(facts.factsDigest(any())).thenReturn("c".repeat(64), "f".repeat(64));

        TenantUtil.runWithTenant(TENANT, () -> { service.receive(ENDPOINT_REF, input); service.receive(ENDPOINT_REF, input); });

        var captured = org.mockito.ArgumentCaptor.forClass(RefundObservation.class);
        verify(results, times(2)).record(eq(TENANT), captured.capture());
        assertNotEquals(captured.getAllValues().get(0).receiptKey(), captured.getAllValues().get(1).receiptKey());
        verify(notifications, times(2)).acknowledgement(target().original().channel(), true);
    }

    @Test
    void unknownEndpointThrowsWithoutProducingAcknowledgement() {
        when(endpoints.resolve(ENDPOINT_REF)).thenReturn(Optional.empty());

        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input)));
        verify(notifications, never()).acknowledgement(any(), anyBoolean());
    }
}
