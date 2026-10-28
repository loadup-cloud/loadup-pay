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
package io.github.loadup.pay.reconciliation.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationOutcome;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationVerificationConverter;
import io.github.loadup.pay.reconciliation.app.model.ApprovedReconciliationReview;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationSubmitCommand;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationVerificationGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationDisposition;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationOutcome;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationState;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReconciliationVerificationServiceTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(ReconciliationVerificationConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final ReconciliationVerificationConverter converter = spring.getBean(ReconciliationVerificationConverter.class);
    final ReconciliationReviewService reviews = mock(ReconciliationReviewService.class);
    final ReconciliationVerificationGateway requests = mock(ReconciliationVerificationGateway.class);
    final ReconciliationPaymentVerification payment = mock(ReconciliationPaymentVerification.class);
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final ReconciliationTransactions tx = new ReconciliationTransactions(new Tx());
    final Clock clock = Clock.fixed(Instant.parse("2026-10-28T06:00:00.123456Z"), ZoneId.systemDefault());
    final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
    final ReconciliationKey key = new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "statement-txn", null);
    final ReconciliationReviewReference reference = new ReconciliationReviewReference("difference", "batch", "run", "b".repeat(64), key.fingerprint());
    final ApprovedReconciliationReview approved = new ApprovedReconciliationReview(reference, scope, key, "owner-request", "proposal", "approval", 2L);
    final AtomicReference<ReconciliationActor> actor = new AtomicReference<>(new ReconciliationActor("tenant", "ops", Set.of("pay:reconciliation:verification:submit", "pay:reconciliation:verification:read", "pay:reconciliation:verification:retry")));
    final AtomicReference<ReconciliationVerificationRequest> stored = new AtomicReference<>();
    final AtomicReference<OutboxRecord> nativeRow = new AtomicReference<>();
    final ReconciliationVerificationService service = new ReconciliationVerificationService(actor::get, reviews, requests, tx, outbox, converter, () -> "request", validation.getValidator(), clock);
    final ReconciliationVerificationDelivery delivery = new ReconciliationVerificationDelivery(outbox, requests, tx, reviews, payment, converter);
    final ReconciliationVerificationSubmitCommand command = new ReconciliationVerificationSubmitCommand("batch", "difference", "proposal", "approval", 2L, "key");

    static void write() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @BeforeEach
    void setup() {
        when(reviews.approvedForVerification(any(), eq("batch"), eq("difference"), eq("proposal"), eq("approval"), eq(2L), anyBoolean())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return approved;
        });
        when(requests.findRequest(anyString(), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(requests.find("tenant", "request")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(requests.lock("tenant", "request")).thenAnswer(call -> {
            write();
            return Optional.ofNullable(stored.get());
        });
        doAnswer(call -> {
            write();
            stored.set(call.getArgument(1));
            return null;
        }).when(requests).insert(anyString(), any());
        doAnswer(call -> {
            write();
            stored.set(call.getArgument(1));
            return null;
        }).when(requests).update(anyString(), any(), anyLong());
        when(outbox.publish(any())).thenAnswer(call -> {
            write();
            OutboxMessage message = call.getArgument(0);
            assertEquals("request", ReconciliationVerificationMessages.decode(message.payload()));
            assertFalse(message.payload().contains("owner-request"));
            nativeRow.set(row("PENDING", 0));
            return "delivery";
        });
        when(outbox.find("tenant", "delivery")).thenAnswer(call -> Optional.ofNullable(nativeRow.get()));
        when(outbox.replay(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            write();
            nativeRow.set(row("PENDING", 0));
            return true;
        });
        when(payment.verify(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertNotNull(stored.get());
            assertEquals(ReconciliationVerificationState.INIT, stored.get().state());
            PaymentVerificationQuery query = call.getArgument(1);
            assertEquals("owner-request", query.merchantPaymentNo());
            return result(query, PaymentVerificationDisposition.OBSERVED);
        });
    }

    @AfterEach
    void close() {
        spring.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void frozenIntentPrecedesRpcAndRecordedUnknownDoesNotResolveTheDifferenceOrRequeryAfterAckLoss() {
        var initial = service.submit(command);
        assertEquals("INIT", initial.state().name());
        assertNull(initial.receipt());
        verifyNoInteractions(payment);
        assertEquals(initial, service.submit(command));
        verify(outbox, times(1)).publish(any());
        nativeRow.set(row("PROCESSING", 1));
        deliver();
        var first = stored.get().receipt();
        assertEquals(ReconciliationVerificationDisposition.OBSERVED, first.disposition());
        assertEquals(ReconciliationVerificationOutcome.UNKNOWN, first.outcome());
        assertEquals("RECORDED", service.detail(new IdQuery("request")).state().name());
        assertEquals(reference, stored.get().reference());
        deliver();
        verify(payment, times(1)).verify(eq("tenant"), any());
        nativeRow.set(row("FAILED", 1));
        service.retry(new ReconciliationVerificationRetryCommand("request", "delivery", "retry lost acknowledgement"));
        nativeRow.set(row("PROCESSING", 1));
        deliver();
        assertEquals(first, stored.get().receipt());
        verify(payment, times(1)).verify(eq("tenant"), any());
    }

    @Test
    void noLocalTargetIsRecordedAsCoverageGapWithoutInventedMoneyOrOutcome() {
        service.submit(command);
        nativeRow.set(row("PROCESSING", 1));
        doAnswer(call -> result(call.getArgument(1), PaymentVerificationDisposition.NO_LOCAL_TARGET)).when(payment).verify(anyString(), any());
        deliver();
        var receipt = stored.get().receipt();
        assertEquals(ReconciliationVerificationDisposition.NO_LOCAL_TARGET, receipt.disposition());
        assertNull(receipt.amount());
        assertNull(receipt.outcome());
        assertNull(receipt.sourceDigest());
    }

    @Test
    void changedReplyScopeOrReclaimedDeliveryDoesNotCompleteTheRequest() {
        service.submit(command);
        nativeRow.set(row("PROCESSING", 1));
        doAnswer(call -> {
            PaymentVerificationQuery q = call.getArgument(1);
            return result(new PaymentVerificationQuery("another-request", q.channel(), q.accountScopeHash(), q.kind(), q.merchantPaymentNo(), q.channelTransactionNo(), q.channelRefundNo()), PaymentVerificationDisposition.NO_LOCAL_TARGET);
        }).when(payment).verify(anyString(), any());
        assertEquals(SCOPE_MISMATCH, assertThrows(CommonException.class, this::deliver).getResultCode());
        assertEquals(ReconciliationVerificationState.INIT, stored.get().state());
        doAnswer(call -> {
            nativeRow.set(row("PROCESSING", 2));
            return result(call.getArgument(1), PaymentVerificationDisposition.OBSERVED);
        }).when(payment).verify(anyString(), any());
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, this::deliver).getResultCode());
        assertNull(stored.get().receipt());
    }

    @Test
    void callerPermissionAndOriginalDeliveryIdentityAreRequired() {
        actor.set(new ReconciliationActor("tenant", "ops", Set.of()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.submit(command)).getResultCode());
        verifyNoInteractions(reviews, requests, outbox, payment);
        actor.set(new ReconciliationActor("tenant", "ops", Set.of("pay:reconciliation:verification:submit", "pay:reconciliation:verification:retry")));
        service.submit(command);
        nativeRow.set(row("FAILED", 1));
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.retry(new ReconciliationVerificationRetryCommand("request", "another-delivery", "reason"))).getResultCode());
        verify(outbox, never()).replay(anyString(), anyString(), anyString(), anyString());
    }

    void deliver() {
        TenantUtil.runWithTenant("tenant", () -> delivery.deliver(new OutboxEvent("delivery", new OutboxMessage("tenant", ReconciliationVerificationMessages.TYPE, 1, "request", ReconciliationVerificationMessages.encode("request")), clock.instant(), null)));
    }

    OutboxRecord row(String state, int attempts) {
        return new OutboxRecord("delivery", "tenant", ReconciliationVerificationMessages.TYPE, "request", state, attempts, null);
    }

    PaymentVerificationDTO result(PaymentVerificationQuery query, PaymentVerificationDisposition disposition) {
        var at = OffsetDateTime.now(clock);
        if (disposition != PaymentVerificationDisposition.OBSERVED)
            return new PaymentVerificationDTO(query, disposition, null, null, null, null, null, null, null, null, null, null, null, null, at, at, null);
        return new PaymentVerificationDTO(query, disposition, "old-revision", "payment", "attempt", null, 3L, "UNKNOWN", PaymentVerificationOutcome.UNKNOWN, Money.ofMinor(100L, "CNY"), null, null, null, "c".repeat(64), at, at, null);
    }

    static class Tx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
