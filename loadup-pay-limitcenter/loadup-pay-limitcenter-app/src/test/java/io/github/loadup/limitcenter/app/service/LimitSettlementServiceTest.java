/*
 * Copyright (C) 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.limitcenter.app.service;

import static io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum.OUTCOME_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.payment.channel.contract.ChannelAmounts;
import io.github.loadup.contracts.event.payment.PaymentFinancialEvidence;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.limitcenter.app.converter.LimitSettlementConverter;
import io.github.loadup.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.limitcenter.app.security.LimitActor;
import io.github.loadup.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.limitcenter.client.enums.LimitReservationState;
import io.github.loadup.limitcenter.domain.gateway.LimitSettlementGateway;
import io.github.loadup.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.limitcenter.domain.model.GateState;
import io.github.loadup.limitcenter.domain.model.LimitBucket;
import io.github.loadup.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.limitcenter.domain.model.LimitReservation;
import io.github.loadup.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.limitcenter.domain.model.LimitSettlementReceipt;
import io.github.loadup.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.limitcenter.domain.policy.LimitWindowPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LimitSettlementServiceTest {
    final LimitServiceFixture fixture = new LimitServiceFixture();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(LimitSettlementConverter.class.getPackageName());
    final PaymentEvidenceClient payment = mock(PaymentEvidenceClient.class);
    final LimitSettlementGateway settlements = mock(LimitSettlementGateway.class);
    final AtomicReference<LimitActor> actor =
            new AtomicReference<>(new LimitActor("payment", Set.of("pay:limit:confirm", "pay:limit:release")));
    final LocalDateTime initiated = fixture.now.minusDays(1);
    final ActivatedLimitPolicy policy = fixture.policy(1, 100000);
    final AtomicReference<StoredLimitReservation> stored = new AtomicReference<>(new StoredLimitReservation(
            LimitReservation.init(
                            "reservation",
                            "operation",
                            "payment-id",
                            "order",
                            "merchant",
                            ChannelAmounts.cnyAmount(1000, "CNY"),
                            "policy",
                            1,
                            initiated,
                            fixture.now.plusMinutes(1))
                    .reserve()
                    .beginExecution("attempt", initiated.plusSeconds(1)),
            policy.policyHash(),
            "b".repeat(64),
            "payment"));
    final AtomicReference<LimitOperationGate> gate =
            new AtomicReference<>(new LimitOperationGate("operation", "payment-id", GateState.OPEN, 0, null));
    final AtomicReference<LimitSettlementReceipt> receipt = new AtomicReference<>();
    final AtomicReference<LimitBucketSet> buckets = new AtomicReference<>();
    final LimitSettlementService service = new LimitSettlementService(
            new LimitTransactions(actor::get, fixture.validation.getValidator(), fixture.manager),
            fixture.reservations,
            fixture.policies,
            settlements,
            payment,
            fixture.converter,
            context.getBean(LimitSettlementConverter.class),
            fixture.generator,
            fixture.validation.getValidator(),
            "payment");

    PaymentFinancialEvidence evidence(PaymentState state, String attempt) {
        return new PaymentFinancialEvidence(
                1,
                "payment-id",
                "order",
                "allocation",
                "operation",
                "merchant",
                1000,
                "CNY",
                state,
                3,
                "proof",
                attempt,
                BusinessTime.external(initiated),
                state == PaymentState.SUCCEEDED ? BusinessTime.external(initiated.plusSeconds(2)) : null,
                state != PaymentState.SUCCEEDED);
    }

    LimitConfirmCommand confirm(PaymentFinancialEvidence proof) {
        return new LimitConfirmCommand(
                "operation",
                "payment-id",
                3,
                "business",
                "proof",
                1000,
                "CNY",
                BusinessTime.external(initiated.plusSeconds(2)),
                proof);
    }

    @BeforeEach
    void setup() {
        var rule = policy.rules().getFirst();
        var key = new LimitBucketKey(
                rule.ruleScopeId(),
                rule.merchantId(),
                rule.metric(),
                rule.currency(),
                rule.window(),
                new LimitWindowPolicy().at(rule.window(), initiated, rule.weekStart()));
        var item = new LimitReservationItem("item", "reservation", key, 1000, 0, 0);
        buckets.set(new LimitBucketSet(Map.of(key, new LimitBucket(key, 0, 1000, 1))));
        when(fixture.reservations.findByOperation("operation")).thenAnswer(c -> Optional.ofNullable(stored.get()));
        when(fixture.reservations.lockGate("operation")).thenAnswer(c -> Optional.of(gate.get()));
        when(fixture.reservations.lockOrCreateGate("operation", "payment-id")).thenAnswer(c -> gate.get());
        when(fixture.reservations.loadItems("reservation")).thenReturn(List.of(item));
        when(fixture.reservations.lockExistingBuckets(any())).thenAnswer(c -> buckets.get());
        when(fixture.policies.lockHead("policy"))
                .thenReturn(Optional.of(new LimitPolicyHead("policy", "merchant", 1, 1)));
        when(fixture.policies.findRevision("policy", 1)).thenReturn(Optional.of(policy));
        when(settlements.findReceipt("operation")).thenAnswer(c -> Optional.ofNullable(receipt.get()));
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    buckets.set(c.getArgument(1));
                    return null;
                })
                .when(fixture.reservations)
                .updateBuckets(any(), any());
        doAnswer(c -> {
                    stored.set(c.getArgument(0));
                    return null;
                })
                .when(fixture.reservations)
                .updateReservation(any(), anyLong());
        doAnswer(c -> {
                    receipt.set(c.getArgument(0));
                    return null;
                })
                .when(settlements)
                .insertReceipt(any());
        doAnswer(c -> {
                    gate.set(c.getArgument(0));
                    return null;
                })
                .when(settlements)
                .fenceGate(any());
    }

    @AfterEach
    void close() {
        context.close();
        fixture.close();
    }

    @Test
    void completeSuccessConfirmsOriginalBucketsAndReplaysWithoutRemoteFacts() {
        var command = confirm(evidence(PaymentState.SUCCEEDED, "attempt"));
        var result = service.confirm(command);
        assertEquals(LimitReservationState.CONFIRMED, result.state());
        assertEquals(result, service.confirm(command));
        assertTrue(buckets.get().buckets().values().stream().allMatch(b -> b.used() == 1000 && b.held() == 0));
        verifyNoInteractions(payment);
        verify(settlements, times(1)).insertReceipt(any());
        verify(fixture.reservations, times(1)).updateBuckets(any(), any());
    }

    @Test
    void completeProofMustBindTheLocallyExecutingAttempt() {
        assertSame(
                OUTCOME_CONFLICT,
                assertThrows(
                                CommonException.class,
                                () -> service.confirm(confirm(evidence(PaymentState.SUCCEEDED, "other-attempt"))))
                        .getResultCode());
        verifyNoInteractions(payment);
        verify(settlements, never()).insertReceipt(any());
        verify(fixture.reservations, never()).updateBuckets(any(), any());
    }

    @Test
    void fencedNoCollectionWithoutAReservationStillCommitsAnIdempotentGate() {
        stored.set(null);
        var command = new LimitReleaseCommand(
                "operation", "payment-id", 3, "business", "proof", "CLOSED", evidence(PaymentState.CLOSED, null));
        var result = service.release(command);
        assertTrue(result.fenced());
        assertEquals(GateState.FENCED, gate.get().state());
        assertEquals(result, service.release(command));
        verifyNoInteractions(payment);
        verify(settlements, times(1)).insertReceipt(any());
        verify(fixture.reservations, never()).updateBuckets(any(), any());
    }

    @Test
    void legacyCommandsStillReadAuthorityOutsideLocalTransactions() {
        var proof = evidence(PaymentState.SUCCEEDED, "attempt");
        when(payment.paymentFacts("payment-id")).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return context.getBean(LimitSettlementConverter.class).toFacts(proof);
        });
        assertEquals(
                LimitReservationState.CONFIRMED, service.confirm(confirm(null)).state());
        verify(payment).paymentFacts("payment-id");
    }

    @Test
    void completeProofCannotGrantPermissionToAnotherService() {
        actor.set(new LimitActor("other", Set.of("pay:limit:confirm")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(
                                CommonException.class,
                                () -> service.confirm(confirm(evidence(PaymentState.SUCCEEDED, "attempt"))))
                        .getResultCode());
        verifyNoInteractions(payment, settlements);
        verify(fixture.reservations, never()).findByOperation(anyString());
    }

    @Test
    void failedReceiptCannotAcknowledgeACommittedFinancialTransition() {
        doThrow(new CommonException(OUTCOME_CONFLICT)).when(settlements).insertReceipt(any());
        assertThrows(
                CommonException.class, () -> service.confirm(confirm(evidence(PaymentState.SUCCEEDED, "attempt"))));
        assertEquals(0, fixture.manager.commits);
        assertEquals(1, fixture.manager.rollbacks);
        // Mock state is not database rollback proof; persistence acceptance remains separate.
    }
}
