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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.*;
import io.github.loadup.pay.acquire.app.port.*;
import io.github.loadup.pay.acquire.app.security.*;
import io.github.loadup.pay.acquire.client.command.*;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.product.client.dto.*;
import io.github.loadup.pay.product.client.enums.AdmissionDecision;
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.common.PaymentMethod;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderCreationServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(AcquireOrderConverter.class.getPackageName());
    final OrderCreationGateway store = mock(OrderCreationGateway.class);
    final OrderAdmissionClient product = mock(OrderAdmissionClient.class);
    final OrderCreationCodec codec = mock(OrderCreationCodec.class);
    final CheckoutSessionGateway sessions = mock(CheckoutSessionGateway.class);
    final CheckoutTokens tokens = mock(CheckoutTokens.class);
    final CheckoutPresentation presentation = mock(CheckoutPresentation.class);
    final AtomicReference<StoredCheckoutSession> session = new AtomicReference<>();
    final AtomicReference<OrderCreateIntent> intent = new AtomicReference<>();
    final AtomicReference<StoredAcquireOrder> order = new AtomicReference<>();
    final AtomicReference<AcquireActor> actor = new AtomicReference<>(new AcquireActor(
            "tenant",
            "b".repeat(64),
            "synthetic",
            Set.of("pay:acquire:create", "pay:acquire:read", "pay:acquire:checkout")));
    final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
    final UnitTransactions manager = new UnitTransactions();
    final AtomicInteger sequence = new AtomicInteger();
    final OrderCreationService service = new OrderCreationService(
            actor::get,
            store,
            product,
            codec,
            context.getBean(AcquireOrderConverter.class),
            new OrderCreationTransactions(manager),
            new CheckoutSessionOperations(
                    sessions,
                    codec,
                    tokens,
                    presentation,
                    context.getBean(AcquireOrderConverter.class),
                    context.getBean(CheckoutConverter.class),
                    () -> "renewed-" + sequence.incrementAndGet(),
                    Clock.fixed(now.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE)),
            () -> "id-" + sequence.incrementAndGet(),
            validation.getValidator(),
            Clock.fixed(now.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE));
    final AcquireOrderCreateCommand command =
            new AcquireOrderCreateCommand("merchant", "M001", "scope", 10000, "CNY", "test order", null);

    final CheckoutCreateCommand checkout =
            new CheckoutCreateCommand("merchant", "M001", "scope", 10000, "CNY", "test order", null, null);

    @BeforeEach
    void setup() {
        when(codec.checkoutDigest(anyString(), anyString(), any())).thenReturn("a".repeat(64));
        when(codec.encodeCheckout(any())).thenReturn("checkout-json");
        when(codec.decodeCheckout(any())).thenReturn(checkout);
        when(codec.decodeAdmission(anyString(), anyString()))
                .thenAnswer(c -> admitted(
                        context.getBean(AcquireOrderConverter.class).toAdmissionCommand(intent.get(), command)));
        when(tokens.keyRef()).thenReturn("checkout");
        when(tokens.activeKeyVersion()).thenReturn("v1");
        when(tokens.nonce()).thenReturn("d".repeat(64));
        when(tokens.issue(any(), anyString(), anyString(), anyString()))
                .thenAnswer(c -> ((CheckoutSession) c.getArgument(0)).id() + "." + "A".repeat(43));
        when(tokens.hash(anyString())).thenReturn("e".repeat(64));
        when(tokens.matches(anyString(), any())).thenReturn(true);
        when(presentation.allowedOrigin()).thenReturn("https://pay.example");
        when(presentation.url(anyString(), anyString(), anyString()))
                .thenAnswer(c -> "https://pay.example/checkout#token=" + c.getArgument(2));
        when(sessions.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(session.get()));
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertNotNull(order.get());
                    assertEquals("INIT", intent.get().state());
                    session.set(c.getArgument(1));
                    return null;
                })
                .when(sessions)
                .insert(eq("tenant"), any());
        when(codec.requestDigest(anyString(), anyString(), any())).thenReturn("a".repeat(64));
        when(codec.encodeIntent(any())).thenReturn("intent-json");
        when(codec.decodeIntent(any())).thenReturn(command);
        when(codec.encodeAdmission(any())).thenReturn("admission-json");
        when(codec.checksum(anyString())).thenReturn("c".repeat(64));
        when(codec.encodeFee(any())).thenReturn("fee-json");
        when(store.findIntent(anyString(), anyString(), anyString()))
                .thenAnswer(call -> Optional.ofNullable(intent.get()));
        when(store.lockIntent(eq("tenant"), anyString())).thenAnswer(call -> intent.get());
        when(store.findOrder(eq("tenant"), anyString())).thenAnswer(call -> Optional.ofNullable(order.get()));
        doAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    intent.set(call.getArgument(1));
                    return null;
                })
                .when(store)
                .insertIntent(eq("tenant"), any());
        doAnswer(call -> {
                    intent.set(call.getArgument(1));
                    return null;
                })
                .when(store)
                .updateIntent(eq("tenant"), any(), anyLong());
        doAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    order.set(call.getArgument(1));
                    return null;
                })
                .when(store)
                .insertOrder(eq("tenant"), any(), any(), anyString());
        when(product.authorize(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, manager.commits);
            return admitted(call.getArgument(0));
        });
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    OrderAdmissionDTO admitted(io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand cmd) {
        return new OrderAdmissionDTO(
                "admission",
                cmd.orderId(),
                "merchant",
                AdmissionDecision.ADMITTED,
                "ALLOW",
                cmd.requestDigest(),
                "contract",
                1,
                1L,
                "snapshot",
                "d".repeat(64),
                BusinessTime.external(now),
                BusinessTime.external(now.plusMinutes(5)),
                terms());
    }

    AcquiringTermsDTO terms() {
        return new AcquiringTermsDTO(
                1,
                new OrderTermsDTO(
                        600, 3600, io.github.loadup.pay.product.client.enums.UnpaidOrderAction.CLOSE, false, 0, 300),
                new PaymentTermsDTO(1, 100000, null, true),
                new FeeTermsDTO(
                        new BigDecimal("0.003"),
                        0,
                        RoundingMode.HALF_UP,
                        0,
                        null,
                        io.github.loadup.pay.product.client.enums.FeeRefundPolicy.NO_RETURN),
                new RefundTermsDTO(
                        true, true, 86400, io.github.loadup.pay.product.client.enums.RefundAllocationPolicy.FIFO),
                new NotificationTermsDTO(false, null, null, 1000, 1, BigDecimal.valueOf(2), 60, 5, 3600),
                new LimitPolicyRefDTO(
                        "policy",
                        1,
                        "e".repeat(64),
                        false,
                        null,
                        io.github.loadup.pay.product.client.enums.RefundAmountRestorePolicy.NO_RESTORE,
                        io.github.loadup.pay.product.client.enums.RefundCountRestorePolicy.NO_RESTORE),
                List.of(new ChannelCapabilityDTO(Channel.WECHAT, AccessMode.DIRECT, Set.of(PaymentMethod.H5))),
                "routing-v1",
                "return-v1");
    }

    @Test
    void commitsA0BeforeNetworkAndMaterializesInitOrderSnapshotAndOneTotalFeeInA1() {
        var dto = service.create(command);
        assertEquals(intent.get().orderId(), dto.orderId());
        assertEquals("INIT", dto.state().name());
        assertEquals(10000, dto.outstandingAmountMinor());
        assertEquals(0, dto.paidAmountMinor());
        assertEquals(now.plusMinutes(10), BusinessTime.local(dto.orderDueAt()));
        assertEquals(2, manager.commits);
        verify(store)
                .insertOrder(
                        eq("tenant"),
                        any(),
                        argThat(fee -> fee.baseAmount().getCent() == 10000
                                && fee.feeAmount().getCent() == 30
                                && fee.state() == FeeState.INIT),
                        eq("fee-json"));
    }

    @Test
    void completedRetryAndDetailDoNotConsultCurrentContractOrRecalculateFee() {
        var first = service.create(command);
        clearInvocations(product, store);
        var replay = service.create(command);
        var read = service.detail(new IdQuery(first.orderId()));
        assertEquals(first, replay);
        assertEquals(first, read);
        verifyNoInteractions(product);
        verify(store, never()).insertOrder(anyString(), any(), any(), anyString());
    }

    @Test
    void dependencyFailureLeavesTheCommittedOriginalIntentAndOrderIdAvailableForRetry() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(product).authorize(any());
        assertThrows(CommonException.class, () -> service.create(command));
        var original = intent.get();
        assertEquals("INIT", original.state());
        assertNull(order.get());
        doAnswer(call -> admitted(call.getArgument(0))).when(product).authorize(any());
        assertEquals(original.orderId(), service.create(command).orderId());
        verify(store, times(1)).insertIntent(eq("tenant"), any());
    }

    @Test
    void deniedDecisionCommitsAStableRejectionAndDoesNotBecomeAnOrderOnRetry() {
        doAnswer(call -> new OrderAdmissionDTO(
                        "admission",
                        intent.get().orderId(),
                        "merchant",
                        AdmissionDecision.DENIED,
                        "CONTRACT_NOT_ADMISSIBLE",
                        "a".repeat(64),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .when(product)
                .authorize(any());
        assertSame(
                CONTRACT_NOT_ADMITTING,
                assertThrows(CommonException.class, () -> service.create(command))
                        .getResultCode());
        assertEquals("REJECTED", intent.get().state());
        assertEquals(2, manager.commits);
        assertNull(order.get());
        clearInvocations(product);
        assertSame(
                CONTRACT_NOT_ADMITTING,
                assertThrows(CommonException.class, () -> service.create(command))
                        .getResultCode());
        verifyNoInteractions(product);
    }

    @Test
    void materializationAtTheExactCutoffCommitsExpiryWithoutCreatingOrderOrFee() {
        doAnswer(call -> {
                    var original = admitted(call.getArgument(0));
                    return new OrderAdmissionDTO(
                            original.admissionId(),
                            original.orderId(),
                            original.payeeMerchantId(),
                            original.decision(),
                            original.reasonCode(),
                            original.requestDigest(),
                            original.contractId(),
                            1,
                            1L,
                            original.snapshotHash(),
                            original.compiledTermsHash(),
                            BusinessTime.external(now.minusMinutes(5)),
                            BusinessTime.external(now),
                            original.terms());
                })
                .when(product)
                .authorize(any());
        assertSame(
                ADMISSION_EXPIRED,
                assertThrows(CommonException.class, () -> service.create(command))
                        .getResultCode());
        assertEquals("EXPIRED", intent.get().state());
        assertEquals(2, manager.commits);
        assertNull(order.get());
        verify(store, never()).insertOrder(anyString(), any(), any(), anyString());
    }

    @Test
    void changedPayloadOnTheSameMerchantOrderIsRejectedBeforeAnotherProductCall() {
        service.create(command);
        clearInvocations(product);
        var changed = new AcquireOrderCreateCommand("merchant", "M001", "scope", 20000, "CNY", "test order", null);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.create(changed))
                        .getResultCode());
        verifyNoInteractions(product);
    }

    @Test
    void anotherSignedPrincipalCannotReuseTheIntentOrReadItsOrder() {
        var dto = service.create(command);
        clearInvocations(product);
        actor.set(
                new AcquireActor("tenant", "c".repeat(64), "other", Set.of("pay:acquire:create", "pay:acquire:read")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.create(command))
                        .getResultCode());
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.detail(new IdQuery(dto.orderId())))
                        .getResultCode());
        verifyNoInteractions(product);
    }

    @Test
    void aMismatchedAdmissionCannotMaterializeTheOrder() {
        doAnswer(call -> admitted(new io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand(
                        "wrong-order", "merchant", "scope", "a".repeat(64), 10000, "CNY", null)))
                .when(product)
                .authorize(any());
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service.create(command))
                        .getResultCode());
        assertEquals("INIT", intent.get().state());
        verify(store, never()).insertOrder(anyString(), any(), any(), anyString());
    }

    @Test
    void rejectsAnOuterTransactionBeforeAnyPersistentIntentOrNetworkCall() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> service.create(command))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(store, product);
    }

    @Test
    void aCompetingA0InsertIsRereadAfterRollbackWithTheWinningOrderId() {
        doAnswer(call -> {
                    intent.set(call.getArgument(1));
                    throw new DuplicateKeyException("synthetic competing insert");
                })
                .when(store)
                .insertIntent(eq("tenant"), any());
        var result = service.create(command);
        assertEquals(result.orderId(), intent.get().orderId());
        assertEquals(1, manager.rollbacks);
        verify(store, times(1)).insertIntent(eq("tenant"), any());
    }

    @Test
    void checkoutCommitsOrderFeeAndOriginalSessionBeforeCompletingTheIntent() {
        var dto = service.checkout(checkout);
        assertEquals("CHECKOUT", intent.get().entryMode());
        assertEquals(intent.get().initialSessionId(), dto.sessionId());
        assertEquals(order.get().order().id(), session.get().session().orderId());
        assertEquals(CheckoutSessionState.ACTIVE, session.get().session().state());
        assertEquals(Set.of("ORDER_SUMMARY"), session.get().scope().permissions());
        assertEquals(2, manager.commits);
        var calls = inOrder(store, sessions);
        calls.verify(store).insertOrder(eq("tenant"), any(), any(), anyString());
        calls.verify(sessions).insert(eq("tenant"), any());
        calls.verify(store).updateIntent(eq("tenant"), argThat(i -> "COMPLETED".equals(i.state())), eq(0L));
    }

    @Test
    void checkoutReplayReturnsTheExactOriginalCapabilityWithoutAnotherAdmissionOrInsert() {
        var first = service.checkout(checkout);
        var initial = session.get();
        when(tokens.activeKeyVersion()).thenReturn("v2");
        clearInvocations(store, sessions, product);
        assertEquals(first, service.checkout(checkout));
        assertSame(initial, session.get());
        verifyNoInteractions(product);
        verify(store, never()).insertOrder(anyString(), any(), any(), anyString());
        verify(sessions, never()).insert(anyString(), any());
    }

    @Test
    void aMerchantOrderNumberCannotSwitchBetweenOrdinaryAndCheckoutEntryPoints() {
        service.create(command);
        clearInvocations(product);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        verifyNoInteractions(product, sessions);
    }

    @Test
    void checkoutCannotSwitchBackToOrdinaryOrChangeItsReturnTargetOnReplay() {
        service.checkout(checkout);
        clearInvocations(product);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.create(command))
                        .getResultCode());
        var changed =
                new CheckoutCreateCommand("merchant", "M001", "scope", 10000, "CNY", "test order", null, "different");
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.checkout(changed))
                        .getResultCode());
        verifyNoInteractions(product);
    }

    @Test
    void signingFailureBeforeOrderWritesKeepsTheStableInitialSessionIdForRetry() {
        doThrow(new CommonException(SESSION_UNAVAILABLE))
                .when(tokens)
                .issue(any(), anyString(), anyString(), anyString());
        assertSame(
                SESSION_UNAVAILABLE,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        var original = intent.get();
        assertEquals("INIT", original.state());
        assertNull(order.get());
        assertEquals(1, manager.rollbacks);
        verify(store, never()).insertOrder(anyString(), any(), any(), anyString());
        verify(sessions, never()).insert(anyString(), any());
        doAnswer(c -> ((CheckoutSession) c.getArgument(0)).id() + "." + "A".repeat(43))
                .when(tokens)
                .issue(any(), anyString(), anyString(), anyString());
        doAnswer(c -> admitted(c.getArgument(0))).when(product).authorize(any());
        assertEquals(original.initialSessionId(), service.checkout(checkout).sessionId());
        verify(store, times(1)).insertIntent(eq("tenant"), any());
    }

    @Test
    void rejectedReturnTargetIsCommittedAndReplayedWithoutCreatingAnOrder() {
        var bad = new CheckoutCreateCommand(
                "merchant", "M001", "scope", 10000, "CNY", "test order", null, "unapproved-target");
        when(codec.decodeCheckout(any())).thenReturn(bad);
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.checkout(bad)).getResultCode());
        assertEquals("REJECTED", intent.get().state());
        assertEquals(2, manager.commits);
        assertNull(order.get());
        clearInvocations(product);
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.checkout(bad)).getResultCode());
        verifyNoInteractions(product, sessions);
    }

    @Test
    void missingOriginalSessionOnCompletedReplayCannotMintAReplacement() {
        service.checkout(checkout);
        session.set(null);
        clearInvocations(sessions, product);
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        verify(sessions, never()).insert(anyString(), any());
        verifyNoInteractions(product);
    }

    @Test
    void sessionInsertFailureRollsBackA1AndNeverMarksTheIntentCompleted() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(sessions).insert(anyString(), any());
        assertSame(
                CONCURRENT_UPDATE,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        assertEquals(1, manager.commits);
        assertEquals(1, manager.rollbacks);
        verify(store, never()).updateIntent(anyString(), any(), anyLong());
        // Mock repositories cannot prove database row rollback; real MySQL acceptance is separate.
    }

    @Test
    void checkoutDenialNeverPersistsAnOrderOrInitialSession() {
        doAnswer(call -> new OrderAdmissionDTO(
                        "admission",
                        intent.get().orderId(),
                        "merchant",
                        AdmissionDecision.DENIED,
                        "CONTRACT_NOT_ADMISSIBLE",
                        "a".repeat(64),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))
                .when(product)
                .authorize(any());
        assertSame(
                CONTRACT_NOT_ADMITTING,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        assertEquals("REJECTED", intent.get().state());
        assertEquals(2, manager.commits);
        assertNull(order.get());
        clearInvocations(product);
        assertSame(
                CONTRACT_NOT_ADMITTING,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        verifyNoInteractions(product);
        verifyNoInteractions(sessions);
    }

    @Test
    void checkoutAtMaterializationCutoffNeverPersistsAnInitialSession() {
        doAnswer(call -> {
                    var original = admitted(call.getArgument(0));
                    return new OrderAdmissionDTO(
                            original.admissionId(),
                            original.orderId(),
                            original.payeeMerchantId(),
                            original.decision(),
                            original.reasonCode(),
                            original.requestDigest(),
                            original.contractId(),
                            1,
                            1L,
                            original.snapshotHash(),
                            original.compiledTermsHash(),
                            BusinessTime.external(now.minusMinutes(5)),
                            BusinessTime.external(now),
                            original.terms());
                })
                .when(product)
                .authorize(any());
        assertSame(
                ADMISSION_EXPIRED,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        assertEquals("EXPIRED", intent.get().state());
        assertEquals(2, manager.commits);
        assertNull(order.get());
        verify(store, never()).insertOrder(anyString(), any(), any(), anyString());
        verifyNoInteractions(sessions);
    }

    @Test
    void checkoutRequiresItsOwnPermissionAndRejectsAnOuterTransactionBeforeA0() {
        actor.set(new AcquireActor("tenant", "b".repeat(64), "synthetic", Set.of("pay:acquire:create")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        actor.set(new AcquireActor("tenant", "b".repeat(64), "synthetic", Set.of("pay:acquire:checkout")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> service.checkout(checkout))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(store, sessions, product);
    }

    @Test
    void admittedButAlreadyDueCheckoutCommitsAnUnpayableRejection() {
        doAnswer(c -> {
                    var original = admitted(c.getArgument(0));
                    return new OrderAdmissionDTO(
                            original.admissionId(),
                            original.orderId(),
                            original.payeeMerchantId(),
                            original.decision(),
                            original.reasonCode(),
                            original.requestDigest(),
                            original.contractId(),
                            original.revision(),
                            original.generation(),
                            original.snapshotHash(),
                            original.compiledTermsHash(),
                            BusinessTime.external(now.minusMinutes(15)),
                            BusinessTime.external(now.plusMinutes(5)),
                            original.terms());
                })
                .when(product)
                .authorize(any());
        assertSame(
                ORDER_NOT_PAYABLE,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        assertEquals("REJECTED", intent.get().state());
        assertNull(order.get());
        assertEquals(2, manager.commits);
        verifyNoInteractions(sessions);
        clearInvocations(product);
        assertSame(
                ORDER_NOT_PAYABLE,
                assertThrows(CommonException.class, () -> service.checkout(checkout))
                        .getResultCode());
        verifyNoInteractions(product);
    }

    static final class UnitTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
