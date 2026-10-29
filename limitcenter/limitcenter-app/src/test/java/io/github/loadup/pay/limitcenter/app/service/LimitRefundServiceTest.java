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
package io.github.loadup.pay.limitcenter.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.limitcenter.app.converter.LimitRefundConverter;
import io.github.loadup.pay.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitRefundGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitSettlementGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.GateState;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitRefundReceipt;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitRestoreResult;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import io.github.loadup.pay.limitcenter.domain.model.LimitSettlementReceipt;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.DEPENDENCY_NOT_READY;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.OUTCOME_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.mockito.Mockito.when;

class LimitRefundServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(LimitRefundConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final MerchantLimitGateway reservations = mock(MerchantLimitGateway.class);
    final LimitPolicyGateway policies = mock(LimitPolicyGateway.class);
    final LimitSettlementGateway settlements = mock(LimitSettlementGateway.class);
    final LimitRefundGateway receipts = mock(LimitRefundGateway.class);
    final PaymentEvidenceClient payment = mock(PaymentEvidenceClient.class);
    final AtomicReference<LimitActor> actor = new AtomicReference<>(new LimitActor("tenant", "payment", Set.of("pay:limit:refund:restore")));
    final LimitServiceFixture.UnitTransactions manager = new LimitServiceFixture.UnitTransactions();
    final LocalDateTime initiated = LocalDateTime.of(2026, 9, 30, 23, 59);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 23, 12, 0);
    final Clock clock = Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final List<LimitRule> rules = List.of(
            new LimitRule("day-amount", "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.DAY, DayOfWeek.MONDAY, 100000, true, true),
            new LimitRule("month-amount", "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.MONTH, DayOfWeek.MONDAY, 100000, true, true),
            new LimitRule("life-amount", "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.LIFETIME, DayOfWeek.MONDAY, 100000, true, true),
            new LimitRule("month-count", "merchant", LimitMetric.PAYMENT_COUNT, "CNY", LimitWindowType.MONTH, DayOfWeek.MONDAY, 100000, true, true));
    final AtomicReference<ActivatedLimitPolicy> policy = new AtomicReference<>(new ActivatedLimitPolicy("policy", 1, "a".repeat(64), "merchant",
            RefundAmountRestorePolicy.ORIGINAL_BUCKETS, RefundCountRestorePolicy.FULL_PAYMENT_REFUNDED_ONCE, rules, initiated.minusDays(1)));
    final LimitReservation confirmed = LimitReservation.init("reservation", "operation", "payment", "order", "merchant",
                    DomainChecks.cnyAmount(10000, "CNY"), "policy", 1, initiated, initiated.plusMinutes(2))
            .reserve().beginExecution("attempt", initiated.plusSeconds(1)).confirm("payment", DomainChecks.cnyAmount(10000, "CNY"), 3);
    final AtomicReference<StoredLimitReservation> stored = new AtomicReference<>(new StoredLimitReservation(confirmed, "a".repeat(64), "b".repeat(64), "payment"));
    final AtomicReference<List<LimitReservationItem>> items = new AtomicReference<>();
    final AtomicReference<LimitBucketSet> buckets = new AtomicReference<>();
    final Map<String, LimitRefundReceipt> accepted = new HashMap<>();
    final AtomicLong cumulative = new AtomicLong(1000);
    final AtomicInteger ids = new AtomicInteger();
    final LimitRefundService service = new LimitRefundService(new LimitTransactions(actor::get, validation.getValidator(), manager),
            reservations, policies, settlements, receipts, payment, context.getBean(LimitRefundConverter.class),
            () -> "receipt-" + ids.incrementAndGet(), clock, "payment");

    LimitRefundRestoreCommand command(String refund, long delivered) {
        return new LimitRefundRestoreCommand("operation", "payment", refund, 2, "event-" + refund, 1000, delivered, "CNY", "proof-" + refund);
    }

    @BeforeEach
    void setup() {
        var windows = new LimitWindowPolicy();
        items.set(rules.stream().map(rule -> {
            var key = new LimitBucketKey(rule.ruleScopeId(), "merchant", rule.metric(), "CNY", rule.window(),
                    windows.at(rule.window(), initiated, rule.weekStart()));
            long quantity = rule.metric() == LimitMetric.AMOUNT ? 10000 : 1;
            return new LimitReservationItem("item-" + rule.ruleScopeId(), "reservation", key, quantity, quantity, 0);
        }).toList());
        var initial = new HashMap<LimitBucketKey, LimitBucket>();
        for (var item : items.get()) initial.put(item.bucket(), new LimitBucket(item.bucket(), item.consumed(), 7, 1));
        buckets.set(new LimitBucketSet(initial));
        when(payment.supportsTenant("tenant")).thenReturn(true);
        when(reservations.findByOperation("tenant", "operation")).thenAnswer(c -> Optional.of(stored.get()));
        when(reservations.lockGate("tenant", "operation")).thenReturn(Optional.of(new LimitOperationGate("operation", "payment", GateState.OPEN, 0, null)));
        when(settlements.findReceipt("tenant", "operation")).thenReturn(Optional.of(
                new LimitSettlementReceipt("confirmation", "operation", "payment", "CONFIRM", "payment-event", "c".repeat(64), 3, "payment-proof")));
        when(policies.lockHead("tenant", "policy")).thenReturn(Optional.of(new LimitPolicyHead("policy", "merchant", 2, 2)));
        when(policies.findRevision("tenant", "policy", 1)).thenAnswer(c -> Optional.of(policy.get()));
        when(reservations.loadItems("tenant", "reservation")).thenAnswer(c -> items.get());
        when(reservations.lockExistingBuckets(eq("tenant"), any())).thenAnswer(c -> buckets.get());
        when(payment.refundFacts(eq("tenant"), anyString())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            String refund = c.getArgument(1);
            var outcome = new RefundOutcomeEventDTO(refund, "request", "payment", "order", "operation", "merchant",
                    1000, "CNY", io.github.loadup.pay.payment.domain.model.RefundState.SUCCEEDED, 2, cumulative.get(), "proof-" + refund);
            return new RefundSettlementFactsDTO(outcome, 10000, 3, "payment-proof", "attempt", BusinessTime.external(initiated), cumulative.get());
        });
        when(receipts.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(accepted.get(c.<String>getArgument(1))));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            buckets.set(c.getArgument(2));
            return null;
        }).when(reservations).updateBuckets(eq("tenant"), any(), any());
        doAnswer(c -> {
            items.set(c.getArgument(2));
            return null;
        }).when(settlements).updateItems(eq("tenant"), any(), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var receipt = c.<LimitRefundReceipt>getArgument(1);
            accepted.put(receipt.refundId(), receipt);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void restoresEachOriginalBucketAndReportsOneLogicalAmountRatherThanSummingWindows() {
        var keys = buckets.get().buckets().keySet();
        var ack = service.restoreRefund(command("refund1", 1000));
        assertEquals(LimitRestoreResult.RESTORED, ack.result());
        assertEquals(1000, ack.restoredAmountMinor());
        assertFalse(ack.restoredPaymentCount());
        assertEquals(keys, buckets.get().buckets().keySet());
        for (var item : items.get()) {
            var bucket = buckets.get().buckets().get(item.bucket());
            assertEquals(7, bucket.held());
            assertEquals(item.bucket().metric() == LimitMetric.AMOUNT ? 9000 : 1, bucket.used());
        }
        verify(policies).findRevision("tenant", "policy", 1);
    }

    @Test
    void fullPaymentRefundRestoresCountOnceAndOlderEventsCannotReduceTheHighWaterMark() {
        cumulative.set(10000);
        var first = service.restoreRefund(command("latest", 1000));
        assertEquals(10000, first.restoredAmountMinor());
        assertTrue(first.restoredPaymentCount());
        cumulative.set(5000); // A request that read an older valid source snapshot before the first local commit.
        var older = service.restoreRefund(command("older", 1000));
        assertEquals(LimitRestoreResult.NO_ACTION, older.result());
        assertEquals(0, older.restoredAmountMinor());
        assertFalse(older.restoredPaymentCount());
        assertTrue(buckets.get().buckets().values().stream().allMatch(b -> b.used() == 0));
    }

    @Test
    void firstAckIsReplayedWithoutQueryingAgainAndChangedOriginalParametersConflict() {
        var original = command("refund1", 1000);
        var first = service.restoreRefund(original);
        when(payment.refundFacts(anyString(), anyString())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertEquals(first, service.restoreRefund(original));
        var changed = new LimitRefundRestoreCommand("operation", "payment", "refund1", 2, "event-refund1", 999, 1000, "CNY", "proof-refund1");
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.restoreRefund(changed)).getResultCode());
        verify(payment, times(1)).refundFacts("tenant", "refund1");
        assertEquals(1, manager.commits);
    }

    @Test
    void aRefundBeforeConfirmIsRetriableAndCannotBeAcknowledged() {
        var pending = LimitReservation.init("reservation", "operation", "payment", "order", "merchant",
                        DomainChecks.cnyAmount(10000, "CNY"), "policy", 1, initiated, initiated.plusMinutes(2)).reserve()
                .beginExecution("attempt", initiated.plusSeconds(1));
        stored.set(new StoredLimitReservation(pending, "a".repeat(64), "b".repeat(64), "payment"));
        assertSame(DEPENDENCY_NOT_READY, assertThrows(CommonException.class, () -> service.restoreRefund(command("refund1", 1000))).getResultCode());
        verify(receipts, never()).insert(anyString(), any());
        verify(reservations, never()).updateBuckets(anyString(), any(), any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void frozenNoRestorePolicyReturnsAnExplicitCommittedNoAction() {
        policy.set(new ActivatedLimitPolicy("policy", 1, "a".repeat(64), "merchant", RefundAmountRestorePolicy.NO_RESTORE,
                RefundCountRestorePolicy.NO_RESTORE, rules, initiated.minusDays(1)));
        var before = buckets.get();
        var ack = service.restoreRefund(command("refund1", 1000));
        assertEquals(LimitRestoreResult.NO_ACTION, ack.result());
        assertEquals(before, buckets.get());
        assertTrue(accepted.containsKey("refund1"));
    }

    @Test
    void unverifiedMoneyOrWrongServiceCannotChangeBuckets() {
        assertSame(OUTCOME_CONFLICT, assertThrows(CommonException.class, () -> service.restoreRefund(command("refund1", 2000))).getResultCode());
        actor.set(new LimitActor("tenant", "other-service", Set.of("pay:limit:refund:restore")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.restoreRefund(command("refund1", 1000))).getResultCode());
        verify(reservations, never()).updateBuckets(anyString(), any(), any());
        assertTrue(accepted.isEmpty());
    }

    @Test
    void failedReceiptPreventsACommittedRestorationAck() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(receipts).insert(anyString(), any());
        assertThrows(CommonException.class, () -> service.restoreRefund(command("refund1", 1000)));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // This in-memory fixture cannot prove database rollback; the production mutations share the same transaction.
    }
}
