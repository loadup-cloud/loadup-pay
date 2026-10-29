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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentChildrenStatus;
import io.github.loadup.pay.payment.domain.model.PaymentControlReceipt;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentInputState;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentControlServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentControlConverter.class.getPackageName());
    final PaymentAcceptanceGateway gates = mock(PaymentAcceptanceGateway.class);
    final PaymentControlGateway store = mock(PaymentControlGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentInputGateway inputs = mock(PaymentInputGateway.class);
    final AtomicReference<PaymentInput> input = new AtomicReference<>();
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final PaymentExecutionLocks executionLocks = mock(PaymentExecutionLocks.class);
    final PaymentActionService actions = mock(PaymentActionService.class);
    final PaymentRecoveryService recovery = mock(PaymentRecoveryService.class);
    final PaymentEvents events = mock(PaymentEvents.class);
    final AtomicReference<StoredPaymentGate> parent = new AtomicReference<>(), gate = new AtomicReference<>();
    final AtomicReference<StoredPaymentAcceptance> payment = new AtomicReference<>();
    final Map<String, PaymentControlReceipt> receipts = new HashMap<>();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor(
            "tenant",
            "acquire",
            Set.of("pay:payment:close", "pay:payment:fence", "pay:payment:fence:read", "pay:payment:accept")));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final AtomicInteger ids = new AtomicInteger();
    final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);
    final PaymentControlService service = new PaymentControlService(
            actor::get,
            gates,
            store,
            attempts,
            inputs,
            codec,
            context.getBean(PaymentAcceptanceConverter.class),
            context.getBean(PaymentControlConverter.class),
            transactions,
            () -> "id-" + ids.incrementAndGet(),
            validation.getValidator(),
            events,
            Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()),
            "acquire");

    @BeforeEach
    void setup() {
        when(inputs.lock("tenant", "input")).thenAnswer(call -> {
            active();
            return Optional.ofNullable(input.get());
        });
        when(gates.lockParent("tenant", "order")).thenAnswer(c -> Optional.ofNullable(parent.get()));
        when(gates.lockPayment("tenant", "payment")).thenAnswer(c -> Optional.ofNullable(gate.get()));
        when(gates.find("tenant", "payment")).thenAnswer(c -> Optional.ofNullable(payment.get()));
        when(store.lockPayment("tenant", "payment")).thenAnswer(c -> Optional.ofNullable(payment.get()));
        when(store.findReceipt(eq("tenant"), anyString(), anyString(), anyString()))
                .thenAnswer(c -> Optional.ofNullable(
                        receipts.get(c.getArgument(1) + ":" + c.getArgument(2) + ":" + c.getArgument(3))));
        when(store.findNoCollectionReceipt("tenant", "payment"))
                .thenAnswer(c -> receipts.values().stream()
                        .filter(r -> "PAYMENT_CLOSE".equals(r.kind()) && r.noCollectionEvidenceId() != null)
                        .findFirst());
        when(store.childrenStatus("tenant", "order")).thenAnswer(c -> {
            var current = payment.get();
            if (current == null) return new PaymentChildrenStatus(0, 0);
            if (Set.of(PaymentState.SUCCEEDED, PaymentState.FAILED, PaymentState.CLOSED)
                    .contains(current.payment().state())
                    && current.terminalEvidenceId() != null) return new PaymentChildrenStatus(0, 0);
            return new PaymentChildrenStatus(1, current.payment().activeAttemptId() == null ? 0 : 1);
        });
        when(codec.decode(any())).thenReturn(PaymentFixtures.command());
        when(codec.checksum(anyString()))
                .thenAnswer(c -> HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(((String) c.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
        doAnswer(c -> {
            active();
            parent.set(c.getArgument(1));
            return null;
        })
                .when(gates)
                .insertParent(eq("tenant"), any());
        doAnswer(c -> {
            active();
            gate.set(c.getArgument(1));
            return null;
        })
                .when(gates)
                .insertPaymentGate(eq("tenant"), any());
        doAnswer(c -> {
            active();
            assertEquals(parent.get().rowVersion(), (Long) c.getArgument(2));
            parent.set(c.getArgument(1));
            return null;
        })
                .when(store)
                .updateParent(eq("tenant"), any(), anyLong());
        doAnswer(c -> {
            active();
            assertEquals(gate.get().rowVersion(), (Long) c.getArgument(2));
            gate.set(c.getArgument(1));
            return null;
        })
                .when(store)
                .updateGate(eq("tenant"), any(), anyLong());
        doAnswer(c -> {
            active();
            assertEquals(payment.get().payment().version(), (Long) c.getArgument(2));
            payment.set(c.getArgument(1));
            return null;
        })
                .when(store)
                .updatePayment(eq("tenant"), any(), anyLong());
        doAnswer(c -> {
            active();
            PaymentControlReceipt value = c.getArgument(1);
            receipts.put(value.kind() + ":" + value.targetId() + ":" + value.requestKey(), value);
            return null;
        })
                .when(store)
                .insertReceipt(eq("tenant"), any());
    }

    void active() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @AfterEach
    void closeContext() {
        context.close();
        validation.close();
    }

    PaymentCloseCommand close(String key, long epoch) {
        return new PaymentCloseCommand("payment", "order", "merchant", key, epoch, "CANCELLED");
    }

    ParentPaymentFenceCommand fence(String key, long epoch) {
        return new ParentPaymentFenceCommand("order", "merchant", epoch, key);
    }

    void accept(PaymentState state) {
        boolean attempted = state != PaymentState.INIT;
        var order = new PaymentOrder(
                "payment",
                "order",
                "allocation",
                "merchant",
                DomainChecks.cnyAmount(4000, "CNY"),
                "operation",
                now,
                state,
                attempted ? "attempt" : null,
                state == PaymentState.SUCCEEDED ? "attempt" : null,
                attempted ? 1 : 0);
        String proof = Set.of(PaymentState.CLOSED, PaymentState.FAILED, PaymentState.SUCCEEDED)
                .contains(state)
                ? "terminal-proof"
                : null;
        payment.set(new StoredPaymentAcceptance(
                order, "admission", "a".repeat(64), "acquire", "payload", "b".repeat(64), proof));
        parent.set(new StoredPaymentGate(
                "parent",
                "merchant",
                "acquire",
                PaymentExecutionGate.init("order", null).open(),
                0));
        gate.set(new StoredPaymentGate(
                "gate",
                "merchant",
                "acquire",
                PaymentExecutionGate.init("order", "payment").open(),
                0));
    }

    PaymentAcceptanceService acceptance() {
        return new PaymentAcceptanceService(
                actor::get,
                gates,
                mock(PaymentInputAccess.class),
                codec,
                context.getBean(PaymentAcceptanceConverter.class),
                transactions,
                () -> "new-" + ids.incrementAndGet(),
                validation.getValidator(),
                Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()),
                "acquire",
                executionLocks,
                actions,
                recovery, events);
    }

    PaymentInputExpireCommand expiryCommand() {
        return new PaymentInputExpireCommand("input", "payment", "order", "allocation", "merchant", 4000, "CNY", "action",
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
    }

    PaymentInput input(LocalDateTime expiresAt) {
        return new PaymentInput("input", "payment", "order", "allocation", "merchant", DomainChecks.cnyAmount(4000, "CNY"),
                "action", Channel.WECHAT, PaymentMethod.H5, "acquire", "v1", "a".repeat(64), "key", 1, "ciphertext",
                now.minusMinutes(5), expiresAt, PaymentInputState.INIT, null, 0L);
    }

    @Test
    void expiredNeverAcceptedInputProducesDurableProofWithoutFencingTheParent() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:expire", "pay:payment:accept")));
        input.set(input(now));
        var result = service.expireInput(expiryCommand());
        assertEquals(PaymentInputExpiryDisposition.EXPIRED_CLOSED, result.disposition());
        assertTrue(result.closed().fenced());
        assertNotNull(result.closed().noCollectionEvidenceId());
        assertNull(result.closed().payment());
        assertNull(payment.get());
        assertEquals(GateState.OPEN, parent.get().gate().state());
        assertEquals(GateState.FENCED, gate.get().gate().state());
        assertEquals(1, receipts.size());
        var receipt = receipts.values().iterator().next();
        assertEquals("input-expiry/allocation", receipt.requestKey());
        assertEquals("INPUT_EXPIRED", receipt.reasonCode());
        clearInvocations(store, gates);
        assertEquals(result, service.expireInput(expiryCommand()));
        verify(store, never()).insertReceipt(anyString(), any());
        verify(gates, never()).insertPaymentGate(anyString(), any());
        verifyNoInteractions(attempts, events);
        assertSame(EXECUTION_FENCED, assertThrows(CommonException.class,
                () -> acceptance().accept(PaymentFixtures.command())).getResultCode());
    }

    @Test
    void activeUnacceptedInputDoesNotCreateAChildFenceOrCloseReceipt() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:expire")));
        input.set(input(now.plusMinutes(1)));
        var result = service.expireInput(expiryCommand());
        assertEquals(PaymentInputExpiryDisposition.INPUT_ACTIVE, result.disposition());
        assertNull(result.closed());
        assertNull(gate.get());
        assertTrue(receipts.isEmpty());
        verifyNoInteractions(attempts, events);
    }

    @Test
    void expiryProofReplayCannotRecreateAMissingParentGate() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:expire")));
        input.set(input(now));
        service.expireInput(expiryCommand());
        parent.set(null);
        clearInvocations(gates);
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.expireInput(expiryCommand())).getResultCode());
        verify(gates, never()).insertParent(anyString(), any());
    }

    @Test
    void inputExpiryLeavesEveryAlreadyAcceptedPaymentUnchanged() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:expire")));
        var source = PaymentFixtures.command();
        var frozen = new io.github.loadup.pay.payment.client.command.PaymentExecuteCommand(source.paymentId(), source.orderId(), source.allocationId(),
                source.admissionId(), source.payeeMerchantId(), source.amountMinor(), source.currency(), source.requestDigest(), source.choice(), "input",
                source.admittedTerms(), source.subject(), source.payableUntil());
        when(codec.decode(any())).thenReturn(frozen);
        for (var state : PaymentState.values()) {
            accept(state);
            input.set(input(now));
            var originalPayment = payment.get();
            var originalGate = gate.get();
            var result = service.expireInput(expiryCommand());
            assertEquals(PaymentInputExpiryDisposition.PAYMENT_EXISTS, result.disposition());
            assertNull(result.closed());
            assertSame(originalPayment, payment.get());
            assertSame(originalGate, gate.get());
            assertEquals(GateState.OPEN, parent.get().gate().state());
        }
        assertTrue(receipts.isEmpty());
        verifyNoInteractions(attempts, events);
    }

    @Test
    void expiryRequiresOwnPermissionAndTheExactOriginalInputScope() {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.expireInput(expiryCommand())).getResultCode());
        verifyNoInteractions(inputs);
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:expire")));
        input.set(input(now));
        var altered = new PaymentInputExpireCommand("input", "payment", "order", "allocation", "merchant", 4001, "CNY", "action",
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.expireInput(altered)).getResultCode());
        assertTrue(receipts.isEmpty());
        assertNull(gate.get());
    }

    @Test
    void closingBeforeAcceptancePersistsATombstoneAndProofWithoutInventingAPayment() {
        var result = service.close(close("close", 1));
        assertTrue(result.fenced());
        assertNull(result.payment());
        assertNotNull(result.noCollectionEvidenceId());
        assertEquals(GateState.FENCED, gate.get().gate().state());
        assertNull(payment.get());
        assertEquals(1, manager.commits);
        var calls = inOrder(gates, store);
        calls.verify(gates).lockParent("tenant", "order");
        calls.verify(gates).insertParent(eq("tenant"), any());
        calls.verify(gates).lockPayment("tenant", "payment");
        calls.verify(gates).insertPaymentGate(eq("tenant"), any());
        calls.verify(store).lockPayment("tenant", "payment");
        calls.verify(store).insertReceipt(eq("tenant"), any());
    }

    @Test
    void theSameCloseKeyReplaysItsOriginalProofWithoutAnotherWrite() {
        var first = service.close(close("close", 1));
        clearInvocations(gates, store);
        assertEquals(first, service.close(close("close", 1)));
        verify(store, never()).insertReceipt(anyString(), any());
        verify(store, never()).updatePayment(anyString(), any(), anyLong());
        verify(gates, never()).insertPaymentGate(anyString(), any());
    }

    @Test
    void aDifferentCloseKeyReusesThePersistedNoCollectionProof() {
        var first = service.close(close("first", 2));
        var next = service.close(close("second", 1));
        assertEquals(first.noCollectionEvidenceId(), next.noCollectionEvidenceId());
        assertEquals(2, next.fenceEpoch());
    }

    @Test
    void changedEpochOrReasonOnTheSameCloseKeyIsRejected() {
        service.close(close("key", 1));
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.close(close("key", 2)))
                        .getResultCode());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(
                        CommonException.class,
                        () -> service.close(
                                new PaymentCloseCommand("payment", "order", "merchant", "key", 1, "TIMEOUT")))
                        .getResultCode());
        assertEquals(1, receipts.size());
    }

    @Test
    void aNeverAttemptedInitPaymentClosesLocallyAndExposesTheSameProofInDetailAndReplay() {
        accept(PaymentState.INIT);
        var original = payment.get();
        var result = service.close(close("close", 1));
        assertEquals("CLOSED", result.payment().state().name());
        assertNotNull(result.noCollectionEvidenceId());
        assertEquals(result.noCollectionEvidenceId(), result.payment().terminalEvidenceId());
        assertEquals(original.payment().initiatedAt(), payment.get().payment().initiatedAt());
        assertEquals(
                original.payment().limitOperationId(), payment.get().payment().limitOperationId());
        assertEquals(2, payment.get().payment().version());
        assertEquals(result.payment(), TenantUtil.callWithTenant(
                "tenant", () -> acceptance().accept(PaymentFixtures.command())));
        service.close(close("close", 1));
        verify(events, times(1)).publish(eq("tenant"), eq(payment.get()), eq(true), any());
    }

    @Test
    void anInitAttemptCanCloseLocallyOnlyBeforeY2Committed() {
        accept(PaymentState.PROCESSING);
        when(attempts.lock("tenant", "attempt")).thenReturn(Optional.of(AttemptFixtures.prepared()));
        var result = service.close(close("before-y2", 1));
        assertEquals("CLOSED", result.payment().state().name());
        assertNotNull(result.noCollectionEvidenceId());
        verify(attempts).update(eq("tenant"), argThat(value -> value.attempt().state() == PaymentAttemptState.CLOSED
                && value.attempt().executionEpoch() == 0 && value.attempt().noCollectionEvidenceId() != null), eq(0L));
        verify(events).publish(eq("tenant"), eq(payment.get()), eq(true), any());
    }

    @Test
    void aCommittedY2AttemptCannotReceiveLocalNoCollectionProof() {
        accept(PaymentState.PROCESSING);
        var submitting = AttemptFixtures.prepared().beginSubmission(1, "b".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        when(attempts.lock("tenant", "attempt")).thenReturn(Optional.of(submitting));
        var result = service.close(close("after-y2", 1));
        assertEquals("CLOSING", result.payment().state().name());
        assertNull(result.noCollectionEvidenceId());
        verify(attempts, never()).update(anyString(), any(), anyLong());
        verify(events).publishSnapshot(eq("tenant"), eq(payment.get()), any());
        verify(events, never()).publish(anyString(), any(), anyBoolean(), any());
    }

    @Test
    void processingAndUnknownPaymentsOnlyEnterClosingAndNeverProduceNoCollectionEvidence() {
        for (var state : List.of(PaymentState.PROCESSING, PaymentState.UNKNOWN)) {
            accept(state);
            var result = service.close(close(state.name(), 1));
            assertEquals("CLOSING", result.payment().state().name());
            assertNull(result.noCollectionEvidenceId());
            assertEquals("attempt", result.payment().activeAttemptId());
        }
    }

    @Test
    void aSucceededPaymentRemainsSucceededAndCannotYieldANoCollectionProof() {
        accept(PaymentState.SUCCEEDED);
        var original = payment.get();
        var result = service.close(close("close", 1));
        assertSame(original, payment.get());
        assertEquals("SUCCEEDED", result.payment().state().name());
        assertEquals("terminal-proof", result.payment().terminalEvidenceId());
        assertNull(result.noCollectionEvidenceId());
        verify(store, never()).updatePayment(anyString(), any(), anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void alreadyClosedOrFailedPaymentsReuseTheirOriginalTerminalProof() {
        for (var state : List.of(PaymentState.CLOSED, PaymentState.FAILED)) {
            accept(state);
            var original = payment.get();
            var result = service.close(close(state.name(), 1));
            assertSame(original, payment.get());
            assertEquals("terminal-proof", result.noCollectionEvidenceId());
        }
        verify(store, never()).updatePayment(anyString(), any(), anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void closingBeforeCreationRejectsTheLateAcceptRequest() {
        service.close(close("close", 1));
        assertSame(
                EXECUTION_FENCED,
                assertThrows(CommonException.class, () -> TenantUtil.callWithTenant(
                        "tenant", () -> acceptance().accept(PaymentFixtures.command())))
                        .getResultCode());
        verify(gates, never()).insert(anyString(), any());
    }

    @Test
    void parentFenceBeforeCreationRejectsLateChildrenAndIsSafeWithNoChildren() {
        var result = service.fenceParent(fence("fence", 3));
        assertTrue(result.fenced());
        assertTrue(result.allChildrenConverged());
        assertEquals(3, result.closeEpoch());
        assertSame(
                EXECUTION_FENCED,
                assertThrows(CommonException.class, () -> TenantUtil.callWithTenant(
                        "tenant", () -> acceptance().accept(PaymentFixtures.command())))
                        .getResultCode());
        verify(gates, never()).insertPaymentGate(anyString(), any());
    }

    @Test
    void parentFenceEpochNeverRegressesAndSameKeyCannotChangeItsIntent() {
        service.fenceParent(fence("new", 5));
        assertEquals(5, service.fenceParent(fence("old", 2)).closeEpoch());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.fenceParent(fence("new", 6)))
                        .getResultCode());
        assertEquals(1, parent.get().rowVersion());
    }

    @Test
    void fencedDoesNotMeanConvergedUntilTheChildHasATrustedTerminalFact() {
        accept(PaymentState.INIT);
        var first = service.fenceParent(fence("fence", 1));
        assertTrue(first.fenced());
        assertFalse(first.allChildrenConverged());
        service.close(close("close", 1));
        assertTrue(service.parentFenceStatus(new ParentFenceStatusQuery("order", 1))
                .allChildrenConverged());
    }

    @Test
    void unknownChildrenKeepTheParentUnconvergedAndRemainCounted() {
        accept(PaymentState.UNKNOWN);
        service.fenceParent(fence("fence", 1));
        service.close(close("close", 1));
        var result = service.parentFenceStatus(new ParentFenceStatusQuery("order", 1));
        assertTrue(result.fenced());
        assertFalse(result.allChildrenConverged());
        assertEquals(1, result.unknownCount());
    }

    @Test
    void missingParentAndHigherRequestedEpochNeverReturnAFenceProof() {
        var missing = service.parentFenceStatus(new ParentFenceStatusQuery("order", 1));
        assertFalse(missing.fenced());
        assertFalse(missing.allChildrenConverged());
        assertNull(parent.get());
        service.fenceParent(fence("fence", 1));
        var newer = service.parentFenceStatus(new ParentFenceStatusQuery("order", 2));
        assertFalse(newer.fenced());
        assertFalse(newer.allChildrenConverged());
    }

    @Test
    void anExistingGateWithoutPaymentOrProofCannotBeCalledNeverAccepted() {
        parent.set(new StoredPaymentGate(
                "parent",
                "merchant",
                "acquire",
                PaymentExecutionGate.init("order", null).open(),
                0));
        gate.set(new StoredPaymentGate(
                "gate",
                "merchant",
                "acquire",
                PaymentExecutionGate.init("order", "payment").open(),
                0));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service.close(close("close", 1)))
                        .getResultCode());
        verify(store, never()).insertReceipt(anyString(), any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void aMissingTombstoneOnReceiptReplayCannotBeRecreatedSilently() {
        service.close(close("close", 1));
        gate.set(null);
        clearInvocations(gates, store);
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service.close(close("close", 1)))
                        .getResultCode());
        verify(gates, never()).insertPaymentGate(anyString(), any());
    }

    @Test
    void theCloseCannotRebindAnOrderOrPayee() {
        accept(PaymentState.INIT);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(
                        CommonException.class,
                        () -> service.close(
                                new PaymentCloseCommand("payment", "order", "other", "close", 1, "CANCELLED")))
                        .getResultCode());
        verify(store, never()).updatePayment(anyString(), any(), anyLong());
    }

    @Test
    void missingPermissionsAndOtherServicesAreRejectedBeforePersistence() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of()));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.close(close("close", 1)))
                        .getResultCode());
        actor.set(new PaymentActor("tenant", "other", Set.of("pay:payment:fence")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.fenceParent(fence("fence", 1)))
                        .getResultCode());
        verifyNoInteractions(gates, store);
    }

    @Test
    void invalidEpochAndOuterTransactionAreRejectedBeforeWriting() {
        assertSame(
                INVALID_ARGUMENT,
                assertThrows(CommonException.class, () -> service.close(close("close", 0)))
                        .getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> service.close(close("close", 1)))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(gates, store);
    }

    @Test
    void aReceiptWriteFailureRollsBackTheControlTransaction() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(store).insertReceipt(anyString(), any());
        assertSame(
                CONCURRENT_UPDATE,
                assertThrows(CommonException.class, () -> service.close(close("close", 1)))
                        .getResultCode());
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Mock ports do not prove that PostgreSQL rolls back gate and payment rows.
    }
}
