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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.app.converter.RoutingProfileConverter;
import io.github.loadup.pay.payment.app.port.RoutingProfileCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.RoutingProfilePublishCommand;
import io.github.loadup.pay.payment.client.dto.RoutingProfileSpecDTO;
import io.github.loadup.pay.payment.client.dto.RoutingRuleDTO;
import io.github.loadup.pay.payment.client.query.RoutingProfileDetailQuery;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.pay.payment.domain.gateway.RoutingProfileGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.RoutingProfile;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import io.github.loadup.pay.payment.domain.model.WechatAccountIdentity;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CHANNEL_BINDING_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ROUTING_PROFILE_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoutingProfileServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RoutingProfileConverter.class.getPackageName());
    final RoutingProfileGateway profiles = mock(RoutingProfileGateway.class);
    final ChannelBindingManagementGateway bindings = mock(ChannelBindingManagementGateway.class);
    final RoutingProfileCodec codec = mock(RoutingProfileCodec.class);
    final Map<String, RoutingProfile> saved = new HashMap<>();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "pay-ops",
            Set.of("pay:payment:routing:publish", "pay:payment:routing:read")));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00.123456Z"), BusinessTime.zone());
    final RoutingProfileService service = new RoutingProfileService(actor::get, profiles, bindings,
            context.getBean(RoutingProfileConverter.class), codec, new PaymentAcceptanceTransactions(manager),
            () -> "profile-" + ids.incrementAndGet(), validation.getValidator(), clock, "pay-ops");

    RoutingRuleDTO rule(String code) {
        return new RoutingRuleDTO(code, Channel.WECHAT, AccessMode.DIRECT, PaymentMethod.H5);
    }

    RoutingProfilePublishCommand command(String ref, String key, List<RoutingRuleDTO> rules) {
        return new RoutingProfilePublishCommand(new RoutingProfileSpecDTO(ref, "merchant", rules), key);
    }

    ChannelMerchantBinding head(String id, String code) {
        return new ChannelMerchantBinding(id, "merchant", code, Channel.WECHAT, AccessMode.DIRECT,
                ChannelBindingState.ACTIVE, "revision-" + code, 1, 100, 1);
    }

    StoredChannelBindingRevision revision(ChannelMerchantBinding head) {
        var binding = new ChannelBindingRevision(head.currentRevisionId(), "merchant", Channel.WECHAT, AccessMode.DIRECT,
                "a".repeat(64), "credential", "v1", new WechatAccountIdentity("app", "mchid", null, null), Set.of(PaymentMethod.H5));
        return new StoredChannelBindingRevision(binding, head.id(), 1, "a".repeat(64), "pay-ops", LocalDateTime.now(clock));
    }

    void binding(String id, String code) {
        var head = head(id, code);
        when(bindings.find("tenant", "merchant", code)).thenReturn(Optional.of(head));
        when(bindings.lock("tenant", "merchant", code)).thenReturn(Optional.of(head));
        when(bindings.revision("tenant", head.currentRevisionId())).thenReturn(Optional.of(revision(head)));
    }

    @BeforeEach
    void setup() {
        binding("binding-primary", "primary");
        when(codec.encode(any())).thenReturn("canonical-profile");
        when(codec.checksum(anyString())).thenReturn("a".repeat(64));
        when(codec.requestDigest(anyString(), anyString(), anyString(), anyString())).thenReturn("b".repeat(64));
        when(profiles.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(saved.get(c.getArgument(1))));
        when(profiles.findRequest(eq("tenant"), anyString(), anyString())).thenAnswer(c -> saved.values().stream()
                .filter(p -> p.callerServiceId().equals(c.getArgument(1)) && p.requestKey().equals(c.getArgument(2))).findFirst());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            RoutingProfile value = c.getArgument(1);
            saved.put(value.spec().profileRef(), value);
            return null;
        }).when(profiles).insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void publishesImmutableDefinitionAndReturnsItsHashAndLocalTime() {
        var result = service.publish(command("routing-v1", "publish", List.of(rule("primary"))));
        assertEquals("routing-v1", result.spec().profileRef());
        assertEquals("a".repeat(64), result.contentHash());
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 0, 0, 123456000), result.publishedAt());
        assertEquals(1, manager.commits);
        assertEquals(1, saved.size());
    }

    @Test
    void oldPublishReplayRemainsReadOnlyEvenWhenBindingHasSinceBeenSuspended() {
        var command = command("routing-v1", "publish", List.of(rule("primary")));
        var result = service.publish(command);
        clearInvocations(bindings, profiles);
        assertEquals(result, service.publish(command));
        verifyNoInteractions(bindings);
        verify(profiles, never()).insert(anyString(), any());
    }

    @Test
    void sameReferenceCannotBeOverwrittenWithAnotherKey() {
        service.publish(command("routing-v1", "first", List.of(rule("primary"))));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "second", List.of(rule("primary"))))).getResultCode());
        assertEquals(1, saved.size());
    }

    @Test
    void sameKeyCannotPublishAnotherReferenceOrReorderRules() {
        binding("binding-secondary", "secondary");
        service.publish(command("routing-v1", "first", List.of(rule("primary"), rule("secondary"))));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "first", List.of(rule("secondary"), rule("primary"))))).getResultCode());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v2", "first", List.of(rule("primary"))))).getResultCode());
    }

    @Test
    void bindingLocksUseStableHeadIdOrderInsteadOfRulePriority() {
        binding("z-binding", "primary");
        binding("a-binding", "secondary");
        var result = service.publish(command("routing-v1", "publish", List.of(rule("primary"), rule("secondary"))));
        var calls = inOrder(bindings);
        calls.verify(bindings).lock("tenant", "merchant", "secondary");
        calls.verify(bindings).lock("tenant", "merchant", "primary");
        assertEquals("primary", result.spec().rules().getFirst().bindingCode());
    }

    @Test
    void missingOrSuspendedBindingBlocksPublication() {
        assertSame(CHANNEL_BINDING_UNAVAILABLE, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "missing", List.of(rule("absent"))))).getResultCode());
        when(bindings.lock("tenant", "merchant", "primary")).thenReturn(Optional.of(head("binding-primary", "primary").suspend()));
        assertSame(CHANNEL_BINDING_UNAVAILABLE, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "stopped", List.of(rule("primary"))))).getResultCode());
        verify(profiles, never()).insert(anyString(), any());
    }

    @Test
    void undeclaredMethodOrModeIsRejectedBeforePublication() {
        var unsupported = new RoutingRuleDTO("primary", Channel.WECHAT, AccessMode.DIRECT, PaymentMethod.JSAPI);
        assertSame(UNSUPPORTED_CAPABILITY, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "publish", List.of(unsupported)))).getResultCode());
        verify(profiles, never()).insert(anyString(), any());
    }

    @Test
    void duplicateRulesAreRejectedInsteadOfCreatingAmbiguousPriority() {
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "publish", List.of(rule("primary"), rule("primary"))))).getResultCode());
        verifyNoInteractions(profiles, bindings);
    }

    @Test
    void detailDoesNotLeakAnotherMerchantsDefinition() {
        service.publish(command("routing-v1", "publish", List.of(rule("primary"))));
        assertSame(ROUTING_PROFILE_UNAVAILABLE, assertThrows(CommonException.class,
                () -> service.detail(new RoutingProfileDetailQuery("other-merchant", "routing-v1"))).getResultCode());
    }

    @Test
    void onlyConfiguredOperationsServiceWithRequiredScopeCanPublish() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:routing:publish")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "publish", List.of(rule("primary"))))).getResultCode());
        actor.set(new PaymentActor("tenant", "pay-ops", Set.of("pay:payment:routing:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                () -> service.publish(command("routing-v1", "publish", List.of(rule("primary"))))).getResultCode());
        verifyNoInteractions(profiles, bindings);
    }

    @Test
    void outerTransactionCannotWrapPublication() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class,
                    () -> service.publish(command("routing-v1", "publish", List.of(rule("primary"))))).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(profiles, bindings);
    }
}
