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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelNotificationAcknowledgement;
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.VerifiedChannelNotification;
import io.github.loadup.pay.payment.app.converter.PaymentCallbackObservationConverter;
import io.github.loadup.pay.payment.app.model.ChannelCallbackEndpoint;
import io.github.loadup.pay.payment.app.model.PaymentResultApplication;
import io.github.loadup.pay.payment.app.port.ChannelCallbackEndpointResolver;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentResultCodec;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelNotificationGateway;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.APPLIED;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentChannelCallbackServiceTest {
    private static final String ENDPOINT_REF = "alipay-primary";
    private static final String TENANT = "tenant";
    private static final String FACTS_DIGEST = "c".repeat(64);

    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentCallbackObservationConverter.class.getPackageName());
    private final ChannelCallbackEndpointResolver endpoints = mock(ChannelCallbackEndpointResolver.class);
    private final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    private final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    private final PaymentChannelNotificationGateway notifications = mock(PaymentChannelNotificationGateway.class);
    private final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
    private final PaymentResultService results = mock(PaymentResultService.class);
    private final PaymentAcceptanceCodec commands = mock(PaymentAcceptanceCodec.class);
    private final PaymentResultCodec observations = mock(PaymentResultCodec.class);
    private final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    private final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    private final AtomicReference<ChannelCallbackEndpoint> endpoint = new AtomicReference<>();
    private final AtomicReference<StoredPaymentAttempt> stored = new AtomicReference<>();
    private final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>();
    private final ChannelNotificationInput input = new ChannelNotificationInput(
            "application/x-www-form-urlencoded", "notify=raw".getBytes(StandardCharsets.UTF_8));
    private final PaymentChannelCallbackService service = new PaymentChannelCallbackService(endpoints, attempts,
            payments, notifications, channels, results, commands, observations,
            context.getBean(PaymentCallbackObservationConverter.class), transactions);

    private ChannelPaymentIdentity identity() { return stored.get().attempt().identity(); }
    private ChannelPaymentTarget target() {
        return new ChannelPaymentTarget(identity(), stored.get().attempt().amount());
    }
    private PaymentObservation query() {
        return new PaymentObservation(identity(), ChannelPaymentOutcome.Kind.SUCCEEDED,
                stored.get().attempt().amount(), "channel-transaction", AttemptFixtures.NOW,
                PaymentObservation.Source.QUERY_RESPONSE, "query-receipt", "a".repeat(64));
    }
    private VerifiedChannelNotification verified(String sourceDigest) {
        return new VerifiedChannelNotification(identity(), stored.get().attempt().amount(), "notification-1",
                "channel-transaction", "CALLBACK_STATUS_SAYS_FAILED", sourceDigest);
    }
    private ChannelNotificationAcknowledgement acknowledgement(boolean success) {
        return new ChannelNotificationAcknowledgement("text/plain", success ? "accepted" : "retry");
    }

    @BeforeEach
    void setup() {
        stored.set(AttemptFixtures.prepared().beginSubmission(1, "d".repeat(64),
                RiskDecision.notConnected(), AttemptFixtures.NOW));
        accepted.set(AttemptFixtures.accepted(true));
        endpoint.set(new ChannelCallbackEndpoint(TENANT, stored.get().attempt().binding().channel(),
                stored.get().attempt().binding().id()));
        when(endpoints.resolve(ENDPOINT_REF)).thenAnswer(call -> Optional.of(endpoint.get()));
        when(attempts.findChannelRequest(anyString(), anyString(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(payments.find(anyString(), anyString())).thenAnswer(call -> {
            if (!TENANT.equals(call.getArgument(0))) return Optional.empty();
            return Optional.ofNullable(accepted.get());
        });
        when(commands.decode(any(StoredPaymentAcceptance.class))).thenReturn(AttemptFixtures.command());
        when(notifications.identify(any(), eq(input))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ChannelNotificationHint(stored.get().attempt().channelRequestNo());
        });
        when(notifications.verify(any(), eq(input))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return verified("b".repeat(64));
        });
        when(channels.query(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return query();
        });
        when(observations.factsDigest(any())).thenReturn(FACTS_DIGEST);
        when(results.record(anyString(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentResultApplication(APPLIED, "result-receipt", null);
        });
        when(notifications.acknowledgement(any(), anyBoolean())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return acknowledgement(call.getArgument(1));
        });
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void verifiesBeforeQueryAndRecordsQueryFactsBeforeReturningNativeProviderAck() {
        var ack = TenantUtil.callWithTenant(TENANT, () -> service.receive(ENDPOINT_REF, input));

        assertEquals(acknowledgement(true), ack);
        var captured = org.mockito.ArgumentCaptor.forClass(PaymentObservation.class);
        verify(results).record(eq(TENANT), captured.capture());
        var callback = captured.getValue();
        assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED, callback.kind());
        assertEquals(PaymentObservation.Source.CALLBACK, callback.source());
        assertEquals("channel-transaction", callback.channelTransactionNo());
        String expectedKey = "notification:" + DigestUtils.sha256(
                "14:notification-1" + FACTS_DIGEST);
        assertEquals(expectedKey, callback.receiptKey());
        assertEquals(DigestUtils.sha256("notification-source:" + "b".repeat(64) + ":" + "a".repeat(64)),
                callback.sourceDigest());
        assertEquals(0, manager.commits);
        assertEquals(0, manager.rollbacks);

        var calls = inOrder(notifications, channels, results);
        verify(endpoints).resolve(ENDPOINT_REF);
        verify(attempts).findChannelRequest(TENANT, endpoint.get().bindingRevisionId(),
                stored.get().attempt().channelRequestNo());
        verify(payments).find(TENANT, stored.get().attempt().paymentId());
        calls.verify(notifications).identify(endpoint.get().channel(), input);
        calls.verify(notifications).verify(target(), input);
        calls.verify(channels).query(target());
        calls.verify(results).record(eq(TENANT), any(PaymentObservation.class));
        calls.verify(notifications).acknowledgement(endpoint.get().channel(), true);
    }

    @Test
    void duplicateNotificationKeepsReceiptKeyForUnchangedFactsWhenRawDigestsChange() {
        var target = target();
        when(notifications.verify(target, input)).thenReturn(verified("b".repeat(64)), verified("e".repeat(64)));
        when(results.record(eq(TENANT), any())).thenReturn(
                new PaymentResultApplication(APPLIED, "first", null),
                new PaymentResultApplication(APPLIED, "second", null));

        TenantUtil.runWithTenant(TENANT, () -> {
            service.receive(ENDPOINT_REF, input);
            service.receive(ENDPOINT_REF, input);
        });

        var captured = org.mockito.ArgumentCaptor.forClass(PaymentObservation.class);
        verify(results, org.mockito.Mockito.times(2)).record(eq(TENANT), captured.capture());
        assertEquals(captured.getAllValues().get(0).receiptKey(), captured.getAllValues().get(1).receiptKey());
        assertFalse(captured.getAllValues().get(0).sourceDigest().equals(captured.getAllValues().get(1).sourceDigest()));
        verify(notifications, org.mockito.Mockito.times(2)).acknowledgement(endpoint.get().channel(), true);
    }

    @Test
    void signatureFailureReturnsNegativeAckWithoutFinancialQuery() {
        doThrow(new CommonException(PaymentResultCodeEnum.SNAPSHOT_INVALID))
                .when(notifications).verify(any(), eq(input));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));
        assertNoFinancialQueryAndNegativeAck();
    }

    @Test
    void verifiedIdentityMismatchReturnsNegativeAckBeforeFinancialQuery() {
        var otherIdentity = new ChannelPaymentIdentity(identity().paymentId(), "other-attempt", identity().channel(),
                identity().bindingRevisionId(), identity().accountScopeHash(), identity().channelRequestNo());
        when(notifications.verify(any(), eq(input))).thenReturn(new VerifiedChannelNotification(otherIdentity,
                target().amount(), "notification-1", "channel-transaction", "PAID", "b".repeat(64)));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));
        assertNoFinancialQueryAndNegativeAck();
    }

    @Test
    void endpointTenantWithoutTheOriginalPaymentCannotQueryChannel() {
        endpoint.set(new ChannelCallbackEndpoint("other-tenant", endpoint.get().channel(), endpoint.get().bindingRevisionId()));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant("other-tenant",
                () -> service.receive(ENDPOINT_REF, input)));
        verify(payments).find("other-tenant", stored.get().attempt().paymentId());
        assertNoFinancialQueryAndNegativeAck();
    }

    @Test
    void endpointBindingMismatchReturnsNegativeAckBeforePaymentQuery() {
        endpoint.set(new ChannelCallbackEndpoint(TENANT, endpoint.get().channel(), "different-revision"));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));
        verify(payments, never()).find(anyString(), anyString());
        assertNoFinancialQueryAndNegativeAck();
    }

    @Test
    void verifiedAmountMismatchReturnsNegativeAckBeforeFinancialQuery() {
        when(notifications.verify(any(), eq(input))).thenReturn(new VerifiedChannelNotification(identity(),
                io.github.loadup.commons.money.Money.ofMinor(1, "CNY"), "notification-1", "channel-transaction",
                "PAID", "b".repeat(64)));

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));
        assertNoFinancialQueryAndNegativeAck();
    }

    @Test
    void zeroExecutionEpochCannotReachNotificationVerificationOrQuery() {
        stored.set(AttemptFixtures.prepared());

        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));
        verify(notifications, never()).verify(any(), any());
        assertNoFinancialQueryAndNegativeAck();
    }

    @Test
    void resultConflictAndRuntimeFailureReturnNegativeProviderAcknowledgements() {
        when(results.record(anyString(), any())).thenReturn(new PaymentResultApplication(CONFLICT, "conflict", null));
        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));

        when(results.record(anyString(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(acknowledgement(false), TenantUtil.callWithTenant(TENANT,
                () -> service.receive(ENDPOINT_REF, input)));
        verify(notifications, org.mockito.Mockito.times(2)).acknowledgement(endpoint.get().channel(), false);
    }

    private void assertNoFinancialQueryAndNegativeAck() {
        verify(channels, never()).query(any());
        verify(results, never()).record(anyString(), any());
        verify(notifications).acknowledgement(endpoint.get().channel(), false);
    }
}
