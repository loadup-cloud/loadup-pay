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
import io.github.loadup.pay.acquire.app.converter.*;
import io.github.loadup.pay.acquire.app.port.*;
import io.github.loadup.pay.acquire.app.security.*;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionCreateCommand;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutSessionServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(CheckoutConverter.class.getPackageName());
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final CheckoutSessionGateway sessions = mock(CheckoutSessionGateway.class);
    final OrderCreationCodec codec = mock(OrderCreationCodec.class);
    final CheckoutTokens tokens = mock(CheckoutTokens.class);
    final CheckoutPresentation presentation = mock(CheckoutPresentation.class);
    final AtomicReference<StoredCheckoutSession> stored = new AtomicReference<>();
    final AtomicReference<AcquireActor> actor =
            new AtomicReference<>(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:session")));
    final AtomicInteger ids = new AtomicInteger();

    CheckoutSessionService service(Clock clock) {
        return new CheckoutSessionService(
                actor::get,
                orders,
                new CheckoutSessionOperations(
                        sessions,
                        codec,
                        tokens,
                        presentation,
                        context.getBean(AcquireOrderConverter.class),
                        context.getBean(CheckoutConverter.class),
                        () -> "session-" + ids.incrementAndGet(),
                        clock),
                new OrderCreationTransactions(new UnitTransactions()),
                validation.getValidator());
    }

    @BeforeEach
    void setup() {
        when(orders.lockOrder("tenant", "order"))
                .thenReturn(order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
        when(codec.decodeAdmission(anyString(), anyString())).thenReturn(admission());
        when(tokens.keyRef()).thenReturn("checkout");
        when(tokens.activeKeyVersion()).thenReturn("v1");
        when(tokens.nonce()).thenReturn("b".repeat(64));
        when(tokens.issue(any(), anyString(), anyString(), anyString()))
                .thenAnswer(c -> ((CheckoutSession) c.getArgument(0)).id() + "." + "A".repeat(43));
        when(tokens.hash(anyString()))
                .thenAnswer(c -> HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(((String) c.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
        when(tokens.matches(anyString(), any())).thenReturn(true);
        when(presentation.allowedOrigin()).thenReturn(ORIGIN);
        when(presentation.url(anyString(), anyString(), anyString()))
                .thenAnswer(c -> ORIGIN + "/checkout#tenant=" + c.getArgument(0) + "&order=" + c.getArgument(1)
                        + "&token=" + c.getArgument(2));
        when(sessions.findRequest(eq("tenant"), eq("order"), anyString()))
                .thenAnswer(
                        c -> stored.get() != null && stored.get().requestKey().equals(c.getArgument(2))
                                ? Optional.of(stored.get())
                                : Optional.empty());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    stored.set(c.getArgument(1));
                    return null;
                })
                .when(sessions)
                .insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    CheckoutSessionCreateCommand command(String key) {
        return new CheckoutSessionCreateCommand("order", key, null);
    }

    @Test
    void createsAnActiveSessionWithSecondAlignedOriginalLifetimeAndOnlySummaryScope() {
        var result = service(clock(NOW)).create(command("request"));
        assertEquals("session-1", result.sessionId());
        assertEquals(
                NOW.plusSeconds(300).truncatedTo(ChronoUnit.SECONDS),
                stored.get().session().expiresAt());
        assertEquals(CheckoutSessionState.ACTIVE, stored.get().session().state());
        assertEquals(Set.of("ORDER_SUMMARY"), stored.get().scope().permissions());
        assertTrue(result.checkoutUrl().contains("#tenant=tenant"));
        assertFalse(stored.get().toString().contains("A".repeat(43)));
    }

    @Test
    void sameRequestReplaysOriginalSessionKeyNonceAndExpiryAfterKeyRotation() {
        var first = service(clock(NOW)).create(command("request"));
        var original = stored.get();
        when(tokens.activeKeyVersion()).thenReturn("v2");
        clearInvocations(sessions, tokens);
        var replay = service(clock(NOW.plusSeconds(5))).create(command("request"));
        assertEquals(first.sessionId(), replay.sessionId());
        assertEquals(first.checkoutUrl(), replay.checkoutUrl());
        assertEquals(first.sessionExpiresAt(), replay.sessionExpiresAt());
        assertSame(original, stored.get());
        verify(sessions, never()).insert(anyString(), any());
        verify(tokens).issue(original.session(), original.tokenNonce(), "checkout", "v1");
    }

    @Test
    void expiredRetryReturnsOriginalCapabilityAndDoesNotExtendIt() {
        var first = service(clock(NOW)).create(command("request"));
        clearInvocations(sessions);
        var replay = service(clock(NOW.plusSeconds(900))).create(command("request"));
        assertEquals(first.checkoutUrl(), replay.checkoutUrl());
        assertEquals(first.sessionExpiresAt(), replay.sessionExpiresAt());
        verify(sessions, never()).insert(anyString(), any());
    }

    @Test
    void paidOrClosedOrdersPermitOnlyExistingRequestReplay() {
        service(clock(NOW)).create(command("request"));
        for (var state : List.of(AcquireOrderState.PAID, AcquireOrderState.CLOSED)) {
            when(orders.lockOrder("tenant", "order"))
                    .thenReturn(order(state, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
            assertEquals(
                    "session-1", service(clock(NOW)).create(command("request")).sessionId());
            assertSame(
                    ORDER_NOT_PAYABLE,
                    assertThrows(
                                    CommonException.class,
                                    () -> service(clock(NOW)).create(command("new")))
                            .getResultCode());
        }
    }

    @Test
    void closePolicyCapsLifetimeButPendingPolicyUsesOriginalSessionWindow() {
        when(orders.lockOrder("tenant", "order"))
                .thenReturn(order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusSeconds(30)));
        service(clock(NOW)).create(command("short"));
        assertEquals(
                NOW.plusSeconds(30).truncatedTo(ChronoUnit.SECONDS),
                stored.get().session().expiresAt());
        when(orders.lockOrder("tenant", "order"))
                .thenReturn(order(AcquireOrderState.PENDING, UnpaidOrderAction.KEEP_PENDING, NOW.minusSeconds(1)));
        service(clock(NOW)).create(command("later"));
        assertEquals(
                NOW.plusSeconds(300).truncatedTo(ChronoUnit.SECONDS),
                stored.get().session().expiresAt());
    }

    @Test
    void differentOwnerCannotIssueEvenWithAnOrderIdAndSessionPermission() {
        actor.set(new AcquireActor("tenant", "b".repeat(64), null, Set.of("pay:acquire:session")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).create(command("request")))
                        .getResultCode());
        verifyNoInteractions(sessions);
    }

    @Test
    void returnTargetMustMatchTheFrozenContractAndNullMeansItsOriginalDefault() {
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(
                                CommonException.class,
                                () -> service(clock(NOW))
                                        .create(new CheckoutSessionCreateCommand(
                                                "order", "key", "https://evil.example")))
                        .getResultCode());
        var first = service(clock(NOW)).create(command("request"));
        assertEquals(
                first.checkoutUrl(),
                service(clock(NOW))
                        .create(new CheckoutSessionCreateCommand("order", "request", "return-v1"))
                        .checkoutUrl());
    }

    @Test
    void aStoredKeyWhoseReconstructedTokenChangedCannotBeSilentlyReissued() {
        service(clock(NOW)).create(command("request"));
        when(tokens.matches(anyString(), any())).thenReturn(false);
        assertSame(
                SESSION_UNAVAILABLE,
                assertThrows(CommonException.class, () -> service(clock(NOW)).create(command("request")))
                        .getResultCode());
        verify(sessions, times(1)).insert(eq("tenant"), any());
    }

    @Test
    void newSessionRequiresPermissionAndNoOuterTransaction() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of()));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).create(command("request")))
                        .getResultCode());
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:session")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(
                                    CommonException.class,
                                    () -> service(clock(NOW)).create(command("request")))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(orders, sessions);
    }
}
