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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.converter.PaymentVerificationConverter;
import io.github.loadup.pay.payment.app.model.PaymentVerificationTarget;
import io.github.loadup.pay.payment.app.port.PaymentVerificationTargetReader;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationOutcome;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentVerificationServiceTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(PaymentVerificationConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final PaymentVerificationTargetReader reader = mock(PaymentVerificationTargetReader.class);
    final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "reconciliation", Set.of("pay:payment:reconciliation:verify")));
    final Clock clock = Clock.fixed(Instant.parse("2026-10-28T06:00:00.123456Z"), ZoneId.systemDefault());
    final PaymentVerificationService service = new PaymentVerificationService(actor::get, reader, channels, new PaymentAcceptanceTransactions(new Tx()), spring.getBean(PaymentVerificationConverter.class), validation.getValidator(), clock, "reconciliation");
    final ChannelPaymentIdentity identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "old-revision", "a".repeat(64), "original-request");
    final Money amount = Money.ofMinor(100L, "CNY");
    final ChannelPaymentTarget target = new ChannelPaymentTarget(identity, amount);
    final PaymentVerificationQuery query = query(PaymentLedgerKind.PAYMENT);

    @AfterEach
    void close() {
        spring.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void localReadIsBoundedReadOnlyAndChannelQueryUsesOnlyOriginalSubmittedTargetOutsideTransaction() {
        when(reader.read("tenant", query)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals("tenant", TenantUtil.getTenantId());
            return new PaymentVerificationTarget(target, null, 7L, "SUCCEEDED", PaymentVerificationDisposition.OBSERVED);
        });
        when(channels.query(target)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return new PaymentObservation(identity, ChannelPaymentOutcome.Kind.SUCCEEDED, amount, "actual-channel-transaction", LocalDateTime.now(clock).minusMinutes(1), PaymentObservation.Source.QUERY_RESPONSE, "receipt", "b".repeat(64));
        });
        var result = service.verify(query);
        assertEquals(query, result.request());
        assertEquals("old-revision", result.bindingRevisionId());
        assertEquals(7L, result.sourceVersion());
        assertEquals("actual-channel-transaction", result.observedChannelTransactionNo());
        assertEquals(PaymentVerificationOutcome.SUCCEEDED, result.outcome());
        assertNotEquals(query.channelTransactionNo(), result.observedChannelTransactionNo());
        verify(channels, never()).submit(any());
        verify(channels, never()).close(any());
        verify(channels, never()).submitRefund(any());
    }

    @Test
    void coverageGapsNeverInventAZeroAmountOrChannelOutcomeAndNeverQueryTheProvider() {
        for (var disposition : List.of(PaymentVerificationDisposition.NO_LOCAL_TARGET, PaymentVerificationDisposition.NOT_SUBMITTED, PaymentVerificationDisposition.AMBIGUOUS_TARGET)) {
            doReturn(new PaymentVerificationTarget(null, null, null, null, disposition)).when(reader).read("tenant", query);
            var result = service.verify(query);
            assertEquals(disposition, result.disposition());
            assertNull(result.outcome());
            assertNull(result.amount());
            assertNull(result.sourceDigest());
        }
        verifyNoInteractions(channels);
    }

    @Test
    void refundConsultationQueriesTheOriginalChildWithoutSubmittingOrApplyingAnyRefund() {
        var query = query(PaymentLedgerKind.REFUND);
        var refund = new PreparedChannelRefund(identity, "refund", "refund-request", Money.ofMinor(30L, "CNY"), amount, "original-transaction");
        when(reader.read("tenant", query)).thenReturn(new PaymentVerificationTarget(null, refund, 3L, "PROCESSING", PaymentVerificationDisposition.OBSERVED));
        when(channels.queryRefund(refund)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new RefundObservation("refund", identity, "refund-request", "original-transaction", ChannelRefundOutcome.Kind.UNKNOWN, refund.amount(), null, null,
                    RefundObservation.Source.QUERY_RESPONSE, "receipt", "b".repeat(64), null);
        });
        var result = service.verify(query);
        assertEquals(PaymentVerificationOutcome.UNKNOWN, result.outcome());
        assertEquals("refund", result.refundId());
        assertNull(result.succeededAt());
        verify(channels, never()).submitRefund(any());
        verify(channels, never()).recoverRefund(any());
        verify(channels, never()).close(any());
    }

    @Test
    void wrongSourceOrScopeCannotBecomeAnObservationAndAuthorizationPrecedesAnyRead() {
        actor.set(new PaymentActor("tenant", "other", actor.get().permissions()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.verify(query)).getResultCode());
        verifyNoInteractions(reader, channels);
        actor.set(new PaymentActor("tenant", "reconciliation", Set.of("pay:payment:reconciliation:verify")));
        when(reader.read("tenant", query)).thenReturn(new PaymentVerificationTarget(target, null, 1L, "UNKNOWN", PaymentVerificationDisposition.OBSERVED));
        when(channels.query(target)).thenReturn(new PaymentObservation(identity, ChannelPaymentOutcome.Kind.UNKNOWN, amount, null, null, PaymentObservation.Source.CLOSE_RESPONSE, "receipt", "b".repeat(64)));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.verify(query)).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> service.verify(query)).getResultCode());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    PaymentVerificationQuery query(PaymentLedgerKind kind) {
        return new PaymentVerificationQuery("source-request", Channel.WECHAT, "a".repeat(64), kind, "original-request", "statement-transaction", kind == PaymentLedgerKind.REFUND ? "refund-request" : null);
    }

    static class Tx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
            assertTrue(definition.isReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, definition.getIsolationLevel());
            assertEquals(10, definition.getTimeout());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
