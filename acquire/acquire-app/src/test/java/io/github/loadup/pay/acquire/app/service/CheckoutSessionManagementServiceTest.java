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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.app.converter.CheckoutSessionManagementConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionRevokeCommand;
import io.github.loadup.pay.acquire.client.query.CheckoutSessionRevocationPageQuery;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionRevocationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionRevocationReceipt;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionState;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.session;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CheckoutSessionManagementServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CheckoutSessionManagementConverter.class.getPackageName());
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final CheckoutSessionGateway sessions = mock(CheckoutSessionGateway.class);
    final CheckoutSessionRevocationGateway receipts = mock(CheckoutSessionRevocationGateway.class);
    final AtomicReference<StoredCheckoutSession> current = new AtomicReference<>(session(CheckoutSessionState.ACTIVE, NOW.plusMinutes(5)));
    final AtomicReference<CheckoutSessionRevocationReceipt> recorded = new AtomicReference<>();
    final AtomicReference<AcquireActor> actor = new AtomicReference<>(new AcquireActor("tenant", OWNER, null,
            Set.of("pay:acquire:session:read", "pay:acquire:session:revoke")));
    final CheckoutSessionManagementService service = new CheckoutSessionManagementService(actor::get, orders, sessions, receipts,
            context.getBean(CheckoutSessionManagementConverter.class), new OrderCreationTransactions(new UnitTransactions()),
            () -> "receipt", validation.getValidator(), clock(NOW));

    private static CheckoutSessionRevokeCommand command(String key, long version, String reason) {
        return new CheckoutSessionRevokeCommand("session", key, version, reason);
    }

    @BeforeEach
    void setup() {
        var order = order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
        when(orders.findOrder("tenant", "order")).thenReturn(Optional.of(order));
        when(orders.lockOrder("tenant", "order")).thenReturn(order);
        when(sessions.find("tenant", "session")).thenAnswer(call -> Optional.of(current.get()));
        when(sessions.lock("tenant", "session")).thenAnswer(call -> Optional.of(current.get()));
        when(receipts.find(eq("tenant"), eq(OWNER), anyString())).thenAnswer(call -> {
            var saved = recorded.get();
            return saved != null && saved.requestKey().equals(call.getArgument(2)) ? Optional.of(saved) : Optional.empty();
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            assertSame(current.get(), call.getArgument(1));
            current.set(call.getArgument(2));
            return null;
        }).when(sessions).revoke(eq("tenant"), any(), any());
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            recorded.set(call.getArgument(1));
            return null;
        }).when(receipts).insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void firstRevocationLocksOrderThenSessionAndWritesMarkerAndReceiptInOneTransaction() {
        var before = current.get();
        var result = service.revoke(command("revoke", 0L, "issuer request"));
        assertTrue(result.newlyRevoked());
        assertEquals(0L, result.beforeVersion());
        assertEquals(1L, result.sessionVersion());
        assertEquals(NOW, current.get().revokedAt());
        assertEquals(before.session(), current.get().session());
        assertEquals(before.tokenHash(), current.get().tokenHash());
        var sequence = inOrder(orders, sessions, receipts);
        sequence.verify(orders).lockOrder("tenant", "order");
        sequence.verify(sessions).lock("tenant", "session");
        sequence.verify(sessions).revoke("tenant", before, current.get());
        sequence.verify(receipts).insert("tenant", recorded.get());
    }

    @Test
    void exactReplayReturnsOriginalReceiptWithoutReadingTheResourceOrCheckingItsCurrentVersion() {
        var command = command("revoke", 0L, "reason");
        var first = service.revoke(command);
        clearInvocations(orders, sessions, receipts);
        assertEquals(first, service.revoke(command));
        verifyNoInteractions(orders, sessions);
        verify(receipts, never()).insert(anyString(), any());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> service.revoke(command("revoke", 0L, "changed"))).getResultCode());
    }

    @Test
    void receiptCommittedWhileWaitingForLocksWinsOverTheOldExpectedVersion() {
        var command = command("revoke", 0L, "reason");
        var first = service.revoke(command);
        var saved = recorded.get();
        clearInvocations(orders, sessions, receipts);
        when(receipts.find("tenant", OWNER, "revoke")).thenReturn(Optional.empty(), Optional.empty(), Optional.of(saved));
        assertEquals(first, service.revoke(command));
        verify(sessions).lock("tenant", "session");
        verify(sessions, never()).revoke(anyString(), any(), any());
        verify(receipts, never()).insert(anyString(), any());
    }

    @Test
    void aDifferentRequestKeepsTheOriginalMarkerButStillRequiresTheCurrentVersion() {
        service.revoke(command("first", 0L, "reason"));
        clearInvocations(sessions);
        assertSame(VERSION_CONFLICT, assertThrows(CommonException.class,
                () -> service.revoke(command("second", 0L, "reason"))).getResultCode());
        var result = service.revoke(command("second", 1L, "another reason"));
        assertFalse(result.newlyRevoked());
        assertEquals(1L, result.beforeVersion());
        assertEquals(1L, result.sessionVersion());
        assertEquals(NOW, current.get().revokedAt());
        verify(sessions, never()).revoke(anyString(), any(), any());
    }

    @Test
    void completedSessionsOnPaidOrdersCanStillBePermanentlyRevoked() {
        current.set(session(CheckoutSessionState.COMPLETED, NOW.plusMinutes(5)));
        var paid = order(AcquireOrderState.PAID, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
        when(orders.lockOrder("tenant", "order")).thenReturn(paid);
        assertTrue(service.revoke(command("revoke", 0L, "reason")).newlyRevoked());
        assertEquals(CheckoutSessionState.COMPLETED, current.get().session().state());
        assertEquals(AcquireOrderState.PAID, paid.order().state());
    }

    @Test
    void detailsExposeMetadataAndAuditHistoryIsBoundToTheOriginalIssuer() {
        service.revoke(command("revoke", 0L, "private-reason"));
        var status = service.detail(new IdQuery("session"));
        assertTrue(status.revoked());
        assertEquals(1L, status.rowVersion());
        assertFalse(status.toString().contains(current.get().tokenHash()));
        assertFalse(status.toString().contains(current.get().tokenNonce()));
        assertFalse(status.toString().contains("private-reason"));
        when(receipts.page("tenant", "session", 1, 20)).thenReturn(PageResult.of(List.of(recorded.get()), 1L, 1, 20));
        var query = new CheckoutSessionRevocationPageQuery("session", null, null);
        assertNotNull(service.revocations(query));
        actor.set(new AcquireActor("tenant", "b".repeat(64), null, Set.of("pay:acquire:session:read", "pay:acquire:session:revoke")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.detail(new IdQuery("session"))).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.revocations(query)).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.revoke(command("other", 1L, "reason"))).getResultCode());
    }

    @Test
    void corruptedAuditOwnershipIsRejected() {
        service.revoke(command("revoke", 0L, "reason"));
        var original = recorded.get();
        var wrongOwner = new CheckoutSessionRevocationReceipt(original.id(), original.sessionId(), original.orderId(), original.requestKey(),
                "b".repeat(64), original.requestDigest(), original.reason(), original.newlyRevoked(), original.beforeVersion(),
                original.sessionVersion(), original.revokedAt(), original.acceptedAt());
        when(receipts.page("tenant", "session", 1, 20)).thenReturn(PageResult.of(List.of(wrongOwner), 1L, 1, 20));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> service.revocations(new CheckoutSessionRevocationPageQuery("session", 1, 20))).getResultCode());
    }

    @Test
    void issuancePermissionDoesNotGrantManagementAccessAndOuterTransactionsAreRejected() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:session")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.detail(new IdQuery("session"))).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.revoke(command("revoke", 0L, "reason"))).getResultCode());
        verifyNoInteractions(orders, sessions, receipts);
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:session:revoke")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.revoke(command("revoke", 0L, "reason"))).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void receiptFailureEscapesTheWriteTransactionInsteadOfReportingSuccess() {
        var failure = new CommonException(SNAPSHOT_INVALID);
        doThrow(failure).when(receipts).insert(eq("tenant"), any());
        assertSame(failure, assertThrows(CommonException.class, () -> service.revoke(command("revoke", 0L, "reason"))));
        assertNull(recorded.get());
    }
}
