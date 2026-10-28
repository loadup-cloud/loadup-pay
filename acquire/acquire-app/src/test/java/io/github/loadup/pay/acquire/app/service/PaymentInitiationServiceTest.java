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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.PaymentInitiationConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentInitiationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.github.loadup.pay.channel.api.model.PayerIdentityType;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.admission;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.AMOUNT_EXCEEDED;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ORDER_NOT_PAYABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.UNSUPPORTED_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentInitiationServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentInitiationConverter.class.getPackageName());
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final PaymentInitiationGateway allocations = mock(PaymentInitiationGateway.class);
    final OrderCreationCodec hashes = mock(OrderCreationCodec.class);
    final PaymentInitiationCodec codec = mock(PaymentInitiationCodec.class);
    final PaymentAcceptanceClient payment = mock(PaymentAcceptanceClient.class);
    final io.github.loadup.pay.acquire.app.port.PaymentClosureClient closure = mock(io.github.loadup.pay.acquire.app.port.PaymentClosureClient.class);
    final OrderSnapshotEvents snapshots = mock(OrderSnapshotEvents.class);
    final AllocationRecoveryQueue recovery = mock(AllocationRecoveryQueue.class);
    final AtomicReference<AcquireActor> actor =
            new AtomicReference<>(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:payment:start")));
    final AtomicReference<StoredAcquireOrder> order =
            new AtomicReference<>(order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
    final Map<String, StoredPaymentAllocation> saved = new HashMap<>();
    final Map<String, PaymentExecuteCommand> commands = new HashMap<>();
    final AtomicInteger ids = new AtomicInteger();
    final UnitTransactions tx = new UnitTransactions();

    PaymentInitiationService service(Clock clock) {
        return new PaymentInitiationService(actor::get, operations(clock));
    }

    PaymentInitiationOperations operations(Clock clock) {
        return new PaymentInitiationOperations(
                orders,
                allocations,
                hashes,
                codec,
                payment,
                closure,
                context.getBean(PaymentInitiationConverter.class),
                new OrderCreationTransactions(tx),
                () -> "id-" + ids.incrementAndGet(),
                validation.getValidator(),
                clock, snapshots, recovery);
    }

    @BeforeEach
    void setup() {
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            StoredPaymentAllocation registered = c.getArgument(1);
            assertEquals(registered, saved.get(registered.allocation().actionKey()));
            assertTrue(c.getArgument(2) instanceof java.time.LocalDateTime);
            return null;
        }).when(recovery).register(eq("tenant"), any(), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(order.get().order().version(), (long) c.getArgument(2));
            return null;
        }).when(snapshots).publish(eq("tenant"), eq("order"), anyLong());
        when(orders.lockOrder("tenant", "order")).thenAnswer(c -> order.get());
        when(hashes.decodeAdmission(anyString(), anyString())).thenReturn(admission());
        when(hashes.checksum(anyString()))
                .thenAnswer(c -> HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(((String) c.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
        when(payment.supportsTenant("tenant")).thenReturn(true);
        when(closure.supportsTenant("tenant")).thenReturn(true);
        when(payment.lookupInput(eq("tenant"), any())).thenReturn(new PaymentInputLookupDTO(false, null));
        when(payment.expireInput(eq("tenant"), any())).thenAnswer(call -> {
            io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand q = call.getArgument(1);
            return new io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO(q.paymentId(), q.orderId(),
                    io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.INPUT_ACTIVE, null);
        });
        when(codec.encode(any())).thenAnswer(c -> {
            PaymentExecuteCommand value = c.getArgument(0);
            commands.put(value.paymentId(), value);
            return value.paymentId();
        });
        when(codec.decode(any()))
                .thenAnswer(c -> commands.get(((StoredPaymentAllocation) c.getArgument(0)).intentPayload()));
        when(allocations.findAction(eq("tenant"), eq("order"), anyString(), anyString()))
                .thenAnswer(c -> Optional.ofNullable(saved.get(c.getArgument(3))));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            AcquireOrder changed = c.getArgument(1);
            assertEquals(order.get().order().version(), (long) c.getArgument(3));
            var old = order.get();
            order.set(new StoredAcquireOrder(
                    changed,
                    old.merchantOrderNo(),
                    old.subject(),
                    old.admission(),
                    old.admittedAt(),
                    old.callerScopeHash(),
                    old.snapshotId(),
                    old.admissionPayload(),
                    old.payloadHash()));
            StoredPaymentAllocation value = c.getArgument(2);
            saved.put(value.allocation().actionKey(), value);
            return null;
        })
                .when(allocations)
                .reserve(eq("tenant"), any(), any(), anyLong());
        when(payment.accept(eq("tenant"), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(tx.commits > 0);
            return response(c.getArgument(1));
        });
    }

    @Test
    void aNativeRecoveryRegistrationFailureRollsBackA2BeforeAnyRemoteCall() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        org.mockito.Mockito.doThrow(failure).when(recovery).register(eq("tenant"), any(), any());
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        var command = h5Command("queue-failure", 4000, new io.github.loadup.pay.payment.client.dto.NoInteractionDTO());
        assertSame(failure, assertThrows(CommonException.class, () -> operations(clock(NOW)).start(principal, command)));
        assertEquals(1, tx.rollbacks);
        verify(payment, never()).accept(anyString(), any());
        verify(payment, never()).prepareInput(anyString(), any());
        verifyNoInteractions(snapshots);
    }

    @Test
    void checkoutUsesTheSharedReservationAndOriginalActionWhileKeepingItsOwnScope() {
        var guarded = new java.util.ArrayList<Boolean>();
        var owner = actor.get();
        var principal = new PaymentInitiationPrincipal("tenant", owner.scopeHash(), "checkout/session", (stored, creating) -> {
            assertEquals(owner.scopeHash(), stored.callerScopeHash());
            guarded.add(creating);
        });
        var command = h5Command("checkout-action", 4000, new io.github.loadup.pay.payment.client.dto.NoInteractionDTO());
        var first = operations(clock(NOW)).start(principal, command);
        var second = operations(clock(NOW)).start(principal, command);
        assertEquals(first.paymentId(), second.paymentId());
        assertEquals("checkout/session", saved.get("checkout-action").allocation().actorScope());
        assertEquals(owner.scopeHash(), saved.get("checkout-action").callerScopeHash());
        assertEquals(1, guarded.stream().filter(Boolean::booleanValue).count());
        verify(recovery, times(1)).register(eq("tenant"), eq(saved.get("checkout-action")), eq(NOW));
        verify(allocations, org.mockito.Mockito.times(1)).reserve(anyString(), any(), any(), anyLong());
    }

    @Test
    void h5SceneInputIsPreparedOutsideTransactionsAndOnlyOpaqueReferenceIsRetainedAcrossReplay() {
        var interaction = new H5SceneInteractionDTO("203.0.113.10", H5SceneType.IOS);
        var inputCalls = new java.util.ArrayList<PaymentInputPrepareCommand>();
        when(payment.prepareInput(eq("tenant"), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            inputCalls.add(c.getArgument(1));
            return new PaymentInputDTO("opaque-prepared-input", "a".repeat(64), BusinessTime.external(NOW.plusMinutes(5)));
        });
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            StoredPaymentAllocation prepared = c.getArgument(2);
            saved.put(prepared.allocation().actionKey(), prepared);
            return null;
        }).when(allocations).attachInput(eq("tenant"), any(), any());

        var command = h5Command("h5", 4000, interaction);
        var first = service(clock(NOW)).startPayment(command);
        var stored = saved.get("h5");
        var frozen = commands.get(first.paymentId());
        var second = service(clock(NOW.plusMinutes(1))).startPayment(command);

        assertEquals(first.paymentId(), second.paymentId());
        assertEquals("opaque-prepared-input", frozen.preparedInputRef());
        assertEquals("opaque-prepared-input", commands.get(first.paymentId()).preparedInputRef());
        assertEquals(first.paymentId(), stored.intentPayload());
        assertFalse(stored.intentPayload().contains(interaction.payerClientIp()));
        assertEquals(2, inputCalls.size());
        var expectedInput = new PaymentInputPrepareCommand(
                first.paymentId(), "order", stored.allocation().id(), frozen.payeeMerchantId(), 4000,
                "CNY", "h5", new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), interaction);
        assertEquals(java.util.List.of(expectedInput, expectedInput), inputCalls);

        var accepted = org.mockito.ArgumentCaptor.forClass(PaymentExecuteCommand.class);
        verify(payment, times(2)).accept(eq("tenant"), accepted.capture());
        assertTrue(accepted.getAllValues().stream().allMatch(value -> "opaque-prepared-input".equals(value.preparedInputRef())));
        verify(allocations, times(1)).reserve(eq("tenant"), any(), any(), anyLong());
        verify(allocations, times(1)).attachInput(eq("tenant"), any(), any());
        verify(snapshots, times(1)).publish(eq("tenant"), eq("order"), anyLong());
    }

    @Test
    void recoveringALostReplyUsesTheExactFrozenInputAndNeverTakesAnotherHold() {
        var guarded = new java.util.ArrayList<Boolean>();
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> guarded.add(creating));
        when(payment.prepareInput(eq("tenant"), any())).thenReturn(new PaymentInputDTO("opaque-input", "a".repeat(64), BusinessTime.external(NOW.plusMinutes(5))));
        doAnswer(call -> {
            StoredPaymentAllocation prepared = call.getArgument(2);
            saved.put(prepared.allocation().actionKey(), prepared);
            return null;
        }).when(allocations).attachInput(eq("tenant"), any(), any());
        when(payment.accept(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        var command = h5Command("lost", 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP));
        assertThrows(CommonException.class, () -> operations(clock(NOW)).start(principal, command));
        var original = saved.get("lost");
        var frozen = commands.get(original.allocation().paymentId());
        when(payment.accept("tenant", frozen)).thenReturn(response(frozen));
        clearInvocations(payment, allocations, snapshots);
        guarded.clear();
        var recovered = operations(clock(NOW.plusHours(1))).recover(principal, "order", "lost", 4000, command.choice());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, recovered.state());
        assertEquals(frozen.paymentId(), recovered.paymentId());
        assertSame(original, saved.get("lost"));
        assertEquals(java.util.List.of(false), guarded);
        verify(payment).accept("tenant", frozen);
        verify(payment, never()).prepareInput(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        verify(allocations, never()).attachInput(anyString(), any(), any());
        verifyNoInteractions(snapshots);
        assertEquals(4000, order.get().order().paymentReservedMinor());
    }

    @Test
    void missingIntentAndPendingInputAreDistinctAndNeitherIsFinancialEvidence() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> assertFalse(creating));
        var choice = new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5);
        var absent = operations(clock(NOW)).recover(principal, "order", "unknown", 4000, choice);
        assertEquals(PaymentIntentRecovery.State.NOT_OBSERVED, absent.state());
        assertNull(absent.paymentId());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        when(payment.prepareInput(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        var start = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        assertThrows(CommonException.class, () -> operations(clock(NOW)).start(start,
                h5Command("pending-input", 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP))));
        clearInvocations(payment, allocations);
        var pending = operations(clock(NOW)).recover(principal, "order", "pending-input", 4000, choice);
        assertEquals(PaymentIntentRecovery.State.INPUT_REQUIRED, pending.state());
        assertNull(pending.paymentId());
        verify(payment, never()).prepareInput(anyString(), any());
        verify(payment, never()).accept(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        assertEquals(4000, order.get().order().paymentReservedMinor());
    }

    PaymentInitiationPrincipal missingPreparation(String key) {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        when(payment.prepareInput(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> operations(clock(NOW)).start(principal,
                h5Command(key, 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP))));
        clearInvocations(payment, allocations, closure);
        return principal;
    }

    @Test
    void missingInputAtTheOriginalDeadlineRequestsOneStableCloseAndReturnsOnlyItsProof() {
        var principal = missingPreparation("missing-deadline");
        var original = saved.get("missing-deadline");
        var execute = commands.get(original.allocation().paymentId());
        var proof = new io.github.loadup.pay.payment.client.dto.PaymentCloseDTO(execute.paymentId(), execute.orderId(), 1L, true, null, "preparation-proof");
        when(closure.close(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            io.github.loadup.pay.payment.client.command.PaymentCloseCommand command = call.getArgument(1);
            assertEquals(execute.paymentId(), command.paymentId());
            assertEquals(execute.payeeMerchantId(), command.payeeMerchantId());
            assertEquals("input-preparation-expiry/" + execute.allocationId(), command.requestKey());
            assertEquals("INPUT_PREPARATION_EXPIRED", command.reasonCode());
            return proof;
        });
        var exactDeadline = execute.payableUntil().atZoneSameInstant(BusinessTime.zone()).toLocalDateTime();
        var result = operations(clock(exactDeadline)).recover(principal, "order", "missing-deadline", 4000, execute.choice());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, result.state());
        assertEquals(proof, result.closed());
        assertEquals(execute.paymentId(), result.paymentId());
        assertEquals(result, operations(clock(exactDeadline.plusHours(1))).recover(principal, "order", "missing-deadline", 4000, execute.choice()));
        assertSame(original, saved.get("missing-deadline"));
        assertEquals(4000L, order.get().order().paymentReservedMinor());
        verify(payment, never()).accept(anyString(), any());
        verify(payment, never()).prepareInput(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        verify(allocations, never()).attachInput(anyString(), any(), any());
    }

    @Test
    void missingPreparationBeforeDeadlineRemainsHeldWithoutFencingOrRequiringNewPayerInput() {
        var principal = missingPreparation("still-preparing");
        var execute = commands.get(saved.get("still-preparing").allocation().paymentId());
        var result = operations(clock(NOW.plusSeconds(1))).recover(principal, "order", "still-preparing", 4000, execute.choice());
        assertEquals(PaymentIntentRecovery.State.INPUT_REQUIRED, result.state());
        verify(closure, never()).close(anyString(), any());
        assertEquals(4000L, order.get().order().paymentReservedMinor());
    }

    @Test
    void failureOrMissingProofFromDeadlineCloseNeverReleasesTheOriginalHold() {
        var principal = missingPreparation("failed-preparation");
        var original = saved.get("failed-preparation");
        var execute = commands.get(original.allocation().paymentId());
        when(closure.close(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> operations(clock(NOW.plusHours(1))).recover(principal, "order", "failed-preparation", 4000, execute.choice()));
        when(closure.close(eq("tenant"), any())).thenReturn(new io.github.loadup.pay.payment.client.dto.PaymentCloseDTO(execute.paymentId(), execute.orderId(), 1L, true, null, null));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> operations(clock(NOW.plusHours(1)))
                .recover(principal, "order", "failed-preparation", 4000, execute.choice())).getResultCode());
        assertSame(original, saved.get("failed-preparation"));
        assertEquals(4000L, order.get().order().paymentReservedMinor());
        verify(payment, never()).accept(anyString(), any());
    }

    @Test
    void acceptanceWinningTheDeadlineRaceUsesAuthoritativePaymentRefreshAndNoAbsentPaymentProof() {
        var principal = missingPreparation("accepted-preparation");
        var execute = commands.get(saved.get("accepted-preparation").allocation().paymentId());
        when(closure.close(eq("tenant"), any())).thenReturn(new io.github.loadup.pay.payment.client.dto.PaymentCloseDTO(
                execute.paymentId(), execute.orderId(), 1L, true, response(execute), null));
        var result = operations(clock(NOW.plusHours(1))).recover(principal, "order", "accepted-preparation", 4000, execute.choice());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, result.state());
        assertEquals(execute.paymentId(), result.paymentId());
        assertNull(result.closed());
        verify(payment, never()).accept(anyString(), any());
    }

    @Test
    void lostInputPreparationReplyIsRecoveredByOriginalScopeWithoutRecreatingPayerInput() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        var command = h5Command("lost-input", 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP));
        when(payment.prepareInput(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> operations(clock(NOW)).start(principal, command));
        var original = saved.get("lost-input");
        var execute = commands.get(original.allocation().paymentId());
        var query = new PaymentInputLookupQuery(execute.paymentId(), execute.orderId(), execute.allocationId(), execute.payeeMerchantId(),
                execute.amountMinor(), execute.currency(), "lost-input", execute.choice());
        var input = new PaymentInputDTO("original-remote-input", "a".repeat(64), BusinessTime.external(NOW.plusMinutes(2)));
        when(payment.lookupInput("tenant", query)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentInputLookupDTO(true, input);
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            StoredPaymentAllocation prepared = call.getArgument(2);
            saved.put(prepared.allocation().actionKey(), prepared);
            return null;
        }).when(allocations).attachInput(eq("tenant"), any(), any());
        clearInvocations(payment, snapshots, allocations);
        var result = operations(clock(NOW.plusMinutes(1))).recover(principal, "order", "lost-input", 4000, command.choice());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, result.state());
        assertEquals(execute.paymentId(), result.paymentId());
        assertEquals("original-remote-input", commands.get(execute.paymentId()).preparedInputRef());
        assertEquals(original.allocation(), saved.get("lost-input").allocation());
        assertEquals(4000, order.get().order().paymentReservedMinor());
        verify(payment).lookupInput("tenant", query);
        verify(payment, never()).prepareInput(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        verify(snapshots, never()).publish(anyString(), anyString(), anyLong());
        verify(allocations).attachInput(eq("tenant"), eq(original), any());
    }

    @Test
    void malformedLookupOrRemoteFailureNeverFallsBackToPreparationOrNewPayment() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        var command = h5Command("input", 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP));
        when(payment.prepareInput(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> operations(clock(NOW)).start(principal, command));
        when(payment.lookupInput(eq("tenant"), any())).thenReturn(new PaymentInputLookupDTO(true, null));
        clearInvocations(payment, allocations);
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> operations(clock(NOW)).recover(principal, "order", "input", 4000, command.choice())).getResultCode());
        when(payment.lookupInput(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class,
                () -> operations(clock(NOW)).recover(principal, "order", "input", 4000, command.choice())).getResultCode());
        verify(payment, never()).prepareInput(anyString(), any());
        verify(payment, never()).accept(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        verify(allocations, never()).attachInput(anyString(), any(), any());
    }

    PaymentInitiationPrincipal preparedCheckout(String key) {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        when(payment.prepareInput(eq("tenant"), any())).thenReturn(new PaymentInputDTO("prepared", "a".repeat(64), BusinessTime.external(NOW.plusMinutes(2))));
        doAnswer(call -> {
            StoredPaymentAllocation prepared = call.getArgument(2);
            saved.put(prepared.allocation().actionKey(), prepared);
            return null;
        }).when(allocations).attachInput(eq("tenant"), any(), any());
        operations(clock(NOW)).start(principal, h5Command(key, 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP)));
        return principal;
    }

    @Test
    void expiredNeverAcceptedInputReturnsTheOriginalProofWithoutReacceptingOrReleasingLocally() {
        var principal = preparedCheckout("expired");
        var original = saved.get("expired");
        var proof = new io.github.loadup.pay.payment.client.dto.PaymentCloseDTO(original.allocation().paymentId(), "order", 1L, true, null, "expiry-proof");
        when(payment.expireInput(eq("tenant"), any())).thenReturn(new io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO(
                original.allocation().paymentId(), "order", io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.EXPIRED_CLOSED, proof));
        clearInvocations(payment, allocations);
        var result = operations(clock(NOW.plusMinutes(3))).recover(principal, "order", "expired", 4000,
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
        assertEquals(proof, result.closed());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, result.state());
        assertEquals(original.allocation().paymentId(), result.paymentId());
        assertSame(original, saved.get("expired"));
        assertEquals(4000, order.get().order().paymentReservedMinor());
        verify(payment, never()).accept(anyString(), any());
        verify(payment, never()).prepareInput(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        var request = org.mockito.ArgumentCaptor.forClass(io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand.class);
        verify(payment).expireInput(eq("tenant"), request.capture());
        assertEquals("prepared", request.getValue().inputRef());
        assertEquals("expired", request.getValue().inputKey());
        assertEquals(original.allocation().id(), request.getValue().allocationId());
    }

    @Test
    void anAlreadyAcceptedPaymentNeedsNoRepeatedAcceptanceAndAnInvalidExpiryProofCannotSucceed() {
        var principal = preparedCheckout("original");
        String paymentId = saved.get("original").allocation().paymentId();
        when(payment.expireInput(eq("tenant"), any())).thenReturn(new io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO(paymentId, "order",
                io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.PAYMENT_EXISTS, null));
        clearInvocations(payment);
        var result = operations(clock(NOW)).recover(principal, "order", "original", 4000, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5));
        assertEquals(paymentId, result.paymentId());
        assertNull(result.closed());
        verify(payment, never()).accept(anyString(), any());
        var invalid = new io.github.loadup.pay.payment.client.dto.PaymentCloseDTO(paymentId, "order", 1L, false, null, "unfenced");
        when(payment.expireInput(eq("tenant"), any())).thenReturn(new io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO(paymentId, "order",
                io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.EXPIRED_CLOSED, invalid));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> operations(clock(NOW)).recover(principal, "order", "original", 4000,
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5))).getResultCode());
        verify(payment, never()).accept(anyString(), any());
    }

    @Test
    void originalInputlessH5RecoveryUsesItsExactDigestAndDoesNotWaitForAnInputThatNeverExisted() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "merchant/" + OWNER, (stored, creating) -> {
        });
        var command = h5Command("inputless", 4000, new io.github.loadup.pay.payment.client.dto.NoInteractionDTO());
        operations(clock(NOW)).start(principal, command);
        var original = saved.get("inputless");
        var frozen = commands.get(original.allocation().paymentId());
        clearInvocations(payment, allocations, snapshots);
        var result = operations(clock(NOW)).recover(principal, "order", "inputless", 4000, command.choice());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, result.state());
        assertEquals(frozen.paymentId(), result.paymentId());
        verify(payment).accept("tenant", frozen);
        verify(payment, never()).lookupInput(anyString(), any());
        verify(payment, never()).expireInput(anyString(), any());
        verify(payment, never()).prepareInput(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        assertSame(original, saved.get("inputless"));
        assertEquals(4000, order.get().order().paymentReservedMinor());
    }

    @Test
    void aReferenceCannotBeAddedToAnIntentWhoseOriginalDigestCommittedNoInput() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "merchant/" + OWNER, (stored, creating) -> {
        });
        var command = h5Command("inputless", 4000, new io.github.loadup.pay.payment.client.dto.NoInteractionDTO());
        operations(clock(NOW)).start(principal, command);
        var paymentId = saved.get("inputless").allocation().paymentId();
        commands.put(paymentId, context.getBean(PaymentInitiationConverter.class).withPreparedInput(commands.get(paymentId), "invented-input"));
        clearInvocations(payment);
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> operations(clock(NOW)).recover(principal, "order", "inputless", 4000, command.choice())).getResultCode());
        verify(payment, never()).accept(anyString(), any());
        verify(payment, never()).lookupInput(anyString(), any());
    }

    @Test
    void recoveryRejectsChangedAmountChoiceAndAnAllocationReturnedFromAnotherScope() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        var command = h5Command("original", 4000, new io.github.loadup.pay.payment.client.dto.NoInteractionDTO());
        operations(clock(NOW)).start(principal, command);
        clearInvocations(payment);
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> operations(clock(NOW)).recover(principal, "order", "original", 4001, command.choice())).getResultCode());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> operations(clock(NOW)).recover(principal, "order", "original", 4000,
                        new PaymentChoiceDTO(Channel.ALIPAY, PaymentMethod.H5))).getResultCode());
        var other = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/other", (stored, creating) -> {
        });
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> operations(clock(NOW)).recover(other, "order", "original", 4000, command.choice())).getResultCode());
        verify(payment, never()).accept(anyString(), any());
        verify(payment, never()).prepareInput(anyString(), any());
    }

    @Test
    void releasedUnacceptedIntentIsResolvedForOutcomeConvergenceWithoutReacceptance() {
        var principal = new PaymentInitiationPrincipal("tenant", OWNER, "checkout/session", (stored, creating) -> {
        });
        var command = h5Command("original", 4000, new io.github.loadup.pay.payment.client.dto.NoInteractionDTO());
        operations(clock(NOW)).start(principal, command);
        var old = saved.get("original");
        var a = old.allocation();
        var released = new OrderPaymentAllocation(a.id(), a.orderId(), a.paymentId(), a.actorScope(), a.actionKey(), a.requestDigest(),
                a.amount(), AllocationState.RELEASED, 0L);
        saved.put("original", new StoredPaymentAllocation(released, old.callerScopeHash(), old.intentPayload(), old.payloadHash(), 1L));
        clearInvocations(payment);
        var result = operations(clock(NOW)).recover(principal, "order", "original", 4000, command.choice());
        assertEquals(PaymentIntentRecovery.State.RESOLVED, result.state());
        assertEquals(a.paymentId(), result.paymentId());
        verify(payment, never()).accept(anyString(), any());
        verify(payment, never()).prepareInput(anyString(), any());
    }

    @Test
    void h5RejectsChannelIdentityInteractionBeforeAnyWrites() {
        var unsupported = h5Command(
                "h5", 4000,
                new ChannelIdentityInteractionDTO(Channel.WECHAT, "openid", "wx-app", PayerIdentityType.OPEN_ID));

        assertSame(
                INVALID_ARGUMENT,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(unsupported))
                        .getResultCode());

        verifyNoInteractions(orders, allocations, payment);
        assertTrue(saved.isEmpty());
    }

    @Test
    void changingH5InteractionKindForSameActionConflictsBeforeAnySecondWrite() {
        service(clock(NOW)).startPayment(command("one", 4000));
        clearInvocations(payment, allocations);

        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(
                        h5Command("one", 4000, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP))))
                        .getResultCode());

        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
        verify(allocations, never()).attachInput(anyString(), any(), any());
        verify(payment, never()).prepareInput(anyString(), any());
        verify(payment, never()).accept(anyString(), any());
        assertEquals(1, saved.size());
    }

    PaymentStartCommand command(String key, long amount) {
        return new PaymentStartCommand(
                "order", key, amount, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), null);
    }

    PaymentStartCommand h5Command(String key, long amount, io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO interaction) {
        return new PaymentStartCommand("order", key, amount,
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), interaction);
    }

    PaymentExecutionDTO response(PaymentExecuteCommand value) {
        return new PaymentExecutionDTO(
                value.paymentId(),
                value.orderId(),
                value.allocationId(),
                value.amountMinor(),
                value.currency(),
                PaymentState.INIT,
                null,
                null,
                null,
                0,
                BusinessTime.external(NOW));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void commitsAmountHoldBeforeSubmittingTheFrozenOriginalTerms() {
        var result = service(clock(NOW)).startPayment(command("one", 4000));
        assertEquals(4000, order.get().order().paymentReservedMinor());
        assertEquals(0, order.get().order().paidAmountMinor());
        assertEquals(1, order.get().order().reservedPaymentCount());
        assertEquals(AcquireOrderState.PENDING, order.get().order().state());
        var allocation = saved.get("one");
        assertEquals(AllocationState.HELD, allocation.allocation().state());
        assertEquals(allocation.allocation().paymentId(), result.paymentId());
        var frozen = commands.get(result.paymentId());
        assertEquals(admission().terms(), frozen.admittedTerms().terms());
        assertEquals(10000, frozen.admittedTerms().orderAmountMinor());
        assertNull(frozen.preparedInputRef());
        verify(snapshots).publish("tenant", "order", order.get().order().version());
    }

    @Test
    void aLostRemoteReplyRetainsTheHoldAndRetryUsesExactlyTheSamePaymentAndTerms() {
        when(payment.accept(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        var original = saved.get("one");
        var frozen = commands.get(original.allocation().paymentId());
        when(payment.accept(eq("tenant"), any())).thenReturn(response(frozen));
        var result = service(clock(NOW.plusHours(1))).startPayment(command("one", 4000));
        assertEquals(frozen.paymentId(), result.paymentId());
        assertSame(original, saved.get("one"));
        assertEquals(4000, order.get().order().paymentReservedMinor());
        verify(allocations, times(1)).reserve(eq("tenant"), any(), any(), anyLong());
        verify(payment, times(2)).accept("tenant", frozen);
        verify(snapshots, times(1)).publish(eq("tenant"), eq("order"), anyLong());
    }

    @Test
    void changedAmountForTheSameActionConflictsBeforeAnySecondSubmission() {
        service(clock(NOW)).startPayment(command("one", 4000));
        clearInvocations(payment);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 5000)))
                        .getResultCode());
        verify(payment, never()).accept(anyString(), any());
        assertEquals(4000, order.get().order().paymentReservedMinor());
    }

    @Test
    void separateActionsShareTheOrderAvailableAmountAndCannotOverReserve() {
        service(clock(NOW)).startPayment(command("one", 4000));
        assertSame(
                AMOUNT_EXCEEDED,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("too-much", 6001)))
                        .getResultCode());
        assertEquals(1, saved.size());
        service(clock(NOW)).startPayment(command("two", 6000));
        assertEquals(10000, order.get().order().paymentReservedMinor());
        assertEquals(2, order.get().order().reservedPaymentCount());
        assertEquals(0, order.get().order().paidAmountMinor());
    }

    @Test
    void malformedRemoteResultDoesNotReleaseTheCommittedHold() {
        when(payment.accept(eq("tenant"), any()))
                .thenReturn(new PaymentExecutionDTO(
                        "foreign",
                        "order",
                        "foreign",
                        4000,
                        "CNY",
                        PaymentState.INIT,
                        null,
                        null,
                        null,
                        0,
                        BusinessTime.external(NOW)));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        assertEquals(4000, order.get().order().paymentReservedMinor());
        assertEquals(1, saved.size());
    }

    @Test
    void deadlinePreventsANewActionButDoesNotPreventExistingActionRecovery() {
        service(clock(NOW)).startPayment(command("one", 4000));
        service(clock(NOW.plusHours(1))).startPayment(command("one", 4000));
        assertSame(
                ORDER_NOT_PAYABLE,
                assertThrows(
                        CommonException.class,
                        () -> service(clock(NOW.plusHours(1))).startPayment(command("new", 1000)))
                        .getResultCode());
        assertEquals(1, saved.size());
    }

    @Test
    void releasedActionOnlyQueriesItsOriginalPaymentAndNeverCreatesAnotherHold() {
        service(clock(NOW)).startPayment(command("one", 4000));
        var held = saved.get("one");
        var value = held.allocation();
        var released = new OrderPaymentAllocation(
                value.id(),
                value.orderId(),
                value.paymentId(),
                value.actorScope(),
                value.actionKey(),
                value.requestDigest(),
                value.amount(),
                AllocationState.RELEASED,
                1);
        saved.put(
                "one",
                new StoredPaymentAllocation(
                        released, held.callerScopeHash(), held.intentPayload(), held.payloadHash(), 1));
        when(payment.detail("tenant", value.paymentId())).thenReturn(response(commands.get(value.paymentId())));
        clearInvocations(payment, allocations);
        assertEquals(
                value.paymentId(),
                service(clock(NOW)).startPayment(command("one", 4000)).paymentId());
        verify(payment).detail("tenant", value.paymentId());
        verify(payment, never()).accept(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
    }

    @Test
    void callerWithoutPermissionCannotReadOrReserveAnOrder() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of()));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        verifyNoInteractions(orders, allocations, payment);
    }

    @Test
    void anotherOwnerAndAnUnconfiguredTenantAreRejectedBeforeHoldingMoney() {
        actor.set(new AcquireActor("tenant", "b".repeat(64), null, Set.of("pay:acquire:payment:start")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        actor.set(new AcquireActor("other", OWNER, null, Set.of("pay:acquire:payment:start")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        verifyNoInteractions(allocations);
    }

    @Test
    void channelOutsideTheOriginalCapabilitiesCannotCreateAnAllocation() {
        var unsupported = new PaymentStartCommand(
                "order", "one", 1000, new PaymentChoiceDTO(Channel.ALIPAY, PaymentMethod.H5), null);
        assertSame(
                UNSUPPORTED_CAPABILITY,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(unsupported))
                        .getResultCode());
        assertTrue(saved.isEmpty());
    }

    @Test
    void anOuterTransactionCannotWrapTheRemoteSubmission() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(
                            CommonException.class,
                            () -> service(clock(NOW)).startPayment(command("one", 4000)))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(orders, allocations, payment);
    }
}
