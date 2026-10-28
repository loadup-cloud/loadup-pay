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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.app.converter.ChannelBindingConverter;
import io.github.loadup.pay.payment.app.port.ChannelBindingCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.ChannelBindingPublishCommand;
import io.github.loadup.pay.payment.client.command.ChannelBindingRevokeCommand;
import io.github.loadup.pay.payment.client.command.ChannelBindingSuspendCommand;
import io.github.loadup.pay.payment.client.dto.AlipayAccountIdentityDTO;
import io.github.loadup.pay.payment.client.query.ChannelBindingDetailQuery;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingChangeReceipt;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChannelBindingServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ChannelBindingConverter.class.getPackageName());
    final ChannelBindingManagementGateway store = mock(ChannelBindingManagementGateway.class);
    final ChannelBindingCodec codec = mock(ChannelBindingCodec.class);
    final AtomicReference<ChannelMerchantBinding> head = new AtomicReference<>();
    final Map<String, StoredChannelBindingRevision> revisions = new HashMap<>();
    final Map<String, ChannelBindingChangeReceipt> receipts = new HashMap<>();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "pay-ops",
            Set.of("pay:payment:binding:publish", "pay:payment:binding:suspend", "pay:payment:binding:revoke", "pay:payment:binding:read")));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00.123456Z"), BusinessTime.zone());
    final ChannelBindingService service = new ChannelBindingService(actor::get, store, context.getBean(ChannelBindingConverter.class),
            codec, new PaymentAcceptanceTransactions(manager), () -> "binding-id-" + ids.incrementAndGet(),
            validation.getValidator(), clock, "pay-ops");

    ChannelBindingPublishCommand publish(String key, long expected, String credentialVersion, int priority) {
        return new ChannelBindingPublishCommand("merchant", "primary", Channel.ALIPAY, AccessMode.DIRECT,
                new AlipayAccountIdentityDTO("app", "seller", null, null, null), "signing-reference", credentialVersion,
                Set.of(PaymentMethod.H5), priority, key, expected);
    }

    @BeforeEach
    void setup() {
        when(store.lock("tenant", "merchant", "primary")).thenAnswer(call -> Optional.ofNullable(head.get()));
        when(store.find("tenant", "merchant", "primary")).thenAnswer(call -> Optional.ofNullable(head.get()));
        when(store.receipt(eq("tenant"), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(receipts.get(call.getArgument(2))));
        when(store.revision(eq("tenant"), anyString())).thenAnswer(call -> Optional.ofNullable(revisions.get(call.getArgument(1))));
        when(codec.accountScopeHash(any(), any())).thenReturn("a".repeat(64));
        when(codec.requestDigest(anyString(), anyString(), anyString(), any())).thenReturn("b".repeat(64));
        when(codec.encodeIdentity(any())).thenReturn("typed-identity-payload");
        when(codec.checksum(anyString())).thenReturn("c".repeat(64));
        doAnswer(call -> {
            active();
            head.set(call.getArgument(1));
            return null;
        }).when(store).insertHead(eq("tenant"), any());
        doAnswer(call -> {
            active();
            assertEquals(head.get().rowVersion(), (Long) call.getArgument(2));
            head.set(call.getArgument(1));
            return null;
        }).when(store).updateHead(eq("tenant"), any(), anyLong());
        doAnswer(call -> {
            active();
            StoredChannelBindingRevision value = call.getArgument(1);
            revisions.put(value.binding().id(), value);
            return null;
        }).when(store).insertRevision(eq("tenant"), any());
        doAnswer(call -> {
            active();
            ChannelBindingChangeReceipt value = call.getArgument(1);
            receipts.put(value.requestKey(), value);
            return null;
        }).when(store).insertReceipt(eq("tenant"), any());
    }

    void active() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void publishesHeadImmutableRevisionAndReceiptInTheSameLocalTransaction() {
        var result = service.publish(publish("first", 0, "v1", 100));
        assertEquals(1, result.revision());
        assertEquals(1, result.appliedVersion());
        assertEquals(ChannelBindingState.ACTIVE, head.get().state());
        assertEquals(result.bindingRevisionId(), head.get().currentRevisionId());
        assertEquals(1, manager.commits);
        var calls = inOrder(store);
        calls.verify(store).insertRevision(eq("tenant"), any());
        calls.verify(store).insertHead(eq("tenant"), any());
        calls.verify(store).insertReceipt(eq("tenant"), any());
    }

    @Test
    void credentialRotationCreatesAnotherRevisionAndKeepsTheOriginalReadable() {
        var original = service.publish(publish("first", 0, "v1", 100));
        var rotated = service.publish(publish("rotate", 1, "v2", 50));
        assertEquals(2, rotated.revision());
        assertEquals(2, rotated.appliedVersion());
        assertEquals("v1", revisions.get(original.bindingRevisionId()).binding().credentialVersion());
        assertEquals("v2", revisions.get(rotated.bindingRevisionId()).binding().credentialVersion());
        assertNotEquals(original.bindingRevisionId(), rotated.bindingRevisionId());
        assertEquals(50, head.get().priority());
    }

    @Test
    void suspendRetainsRevisionAndOldPublishReplayReturnsOriginalAppliedSnapshot() {
        var command = publish("first", 0, "v1", 100);
        var original = service.publish(command);
        var suspended = service.suspend(new ChannelBindingSuspendCommand("merchant", "primary", "stop", 1));
        assertEquals(ChannelBindingState.SUSPENDED, head.get().state());
        assertEquals(original.bindingRevisionId(), suspended.bindingRevisionId());
        assertEquals(original, service.publish(command));
        assertEquals(2, head.get().rowVersion());
        verify(store, times(1)).insertRevision(anyString(), any());
    }

    @Test
    void permanentRevokeCannotBeReactivatedButOldPublishReplayStaysReadOnly() {
        var command = publish("first", 0, "v1", 100);
        var original = service.publish(command);
        service.revoke(new ChannelBindingRevokeCommand("merchant", "primary", "revoke", 1));
        assertEquals(ChannelBindingState.REVOKED, head.get().state());
        assertEquals(original, service.publish(command));
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> service.publish(publish("reactivate", 2, "v2", 100))).getResultCode());
        assertEquals(1, revisions.size());
    }

    @Test
    void sameKeyCannotChangePayloadOrOperationKind() {
        service.publish(publish("first", 0, "v1", 100));
        var changed = publish("first", 0, "v1", 50);
        when(codec.requestDigest("tenant", "pay-ops", "PUBLISH", changed)).thenReturn("d".repeat(64));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.publish(changed)).getResultCode());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> service.suspend(new ChannelBindingSuspendCommand("merchant", "primary", "first", 1))).getResultCode());
    }

    @Test
    void staleExpectedVersionCannotPublishAnotherRevision() {
        service.publish(publish("first", 0, "v1", 100));
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class,
                () -> service.publish(publish("stale", 0, "v2", 100))).getResultCode());
        assertEquals(1, revisions.size());
        assertEquals(1, head.get().rowVersion());
    }

    @Test
    void directModeCannotCarryProviderAuthorization() {
        var invalid = new ChannelBindingPublishCommand("merchant", "primary", Channel.ALIPAY, AccessMode.DIRECT,
                new AlipayAccountIdentityDTO("app", "seller", null, "merchant-authorization", "v1"),
                "signing-reference", "v1", Set.of(PaymentMethod.H5), 100, "first", 0);
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.publish(invalid)).getResultCode());
        verifyNoInteractions(store);
    }

    @Test
    void typedIdentityRejectsAnotherChannelBeforeBindingWrites() {
        var invalid = new ChannelBindingPublishCommand("merchant", "primary", Channel.WECHAT, AccessMode.DIRECT,
                new AlipayAccountIdentityDTO("app", "seller", null, null, null),
                "signing-reference", "v1", Set.of(PaymentMethod.JSAPI), 100, "first", 0);
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.publish(invalid)).getResultCode());
        verifyNoInteractions(store);
    }

    @Test
    void detailReturnsRouteMetadataWithoutCredentialOrIdentityPayload() {
        service.publish(publish("first", 0, "v1", 100));
        var result = service.detail(new ChannelBindingDetailQuery("merchant", "primary"));
        assertEquals(1, result.revision());
        assertEquals(Set.of(PaymentMethod.H5), result.capabilities());
        assertFalse(result.toString().contains("signing-reference"));
        assertFalse(result.toString().contains("seller"));
    }

    @Test
    void serviceAndOuterTransactionAreRejectedBeforeBindingAccess() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:binding:publish")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                () -> service.publish(publish("first", 0, "v1", 100))).getResultCode());
        actor.set(new PaymentActor("tenant", "pay-ops", Set.of("pay:payment:binding:publish")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class,
                    () -> service.publish(publish("first", 0, "v1", 100))).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(store);
    }

    @Test
    void receiptFailureRollsBackThePublishBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(store).insertReceipt(eq("tenant"), any());
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class,
                () -> service.publish(publish("first", 0, "v1", 100))).getResultCode());
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Mock ports establish the boundary; real PostgreSQL rollback remains an integration acceptance item.
    }
}
