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
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.RoutingProfileGateway;
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.github.loadup.pay.payment.domain.model.ChannelMerchantBinding;
import io.github.loadup.pay.payment.domain.model.RoutableChannelBinding;
import io.github.loadup.pay.payment.domain.model.RoutingProfile;
import io.github.loadup.pay.payment.domain.model.RoutingProfileSpec;
import io.github.loadup.pay.payment.domain.model.RoutingRule;
import io.github.loadup.pay.payment.domain.model.StoredChannelBindingRevision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CHANNEL_BINDING_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ROUTING_PROFILE_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRouteSelectorTest {
    final ChannelBindingGateway bindings = mock(ChannelBindingGateway.class);
    final RoutingProfileGateway profiles = mock(RoutingProfileGateway.class);
    final PaymentRouteSelector selector = new PaymentRouteSelector(bindings, profiles);

    RoutingRule rule(String code) {
        return new RoutingRule(code, Channel.WECHAT, AccessMode.DIRECT, PaymentMethod.H5);
    }

    RoutingProfile profile(String merchant, List<RoutingRule> rules) {
        return new RoutingProfile("profile", new RoutingProfileSpec("routing-v1", merchant, rules),
                "a".repeat(64), "pay-ops", "publish", "b".repeat(64), LocalDateTime.of(2026, 10, 22, 12, 0));
    }

    RoutableChannelBinding candidate(String code, int priority) {
        var binding = new ChannelBindingRevision("revision-" + code, "merchant", Channel.WECHAT, AccessMode.DIRECT,
                "a".repeat(64), "credential", "v1", new WechatAccountIdentity("app", "mchid", null, null), Set.of(PaymentMethod.H5));
        var head = new ChannelMerchantBinding("binding-" + code, "merchant", code, binding.channel(), binding.accessMode(),
                ChannelBindingState.ACTIVE, binding.id(), 1, priority, 1);
        return new RoutableChannelBinding(head, new StoredChannelBindingRevision(binding, head.id(), 1, "b".repeat(64),
                "pay-ops", LocalDateTime.of(2026, 10, 22, 12, 0)));
    }

    @BeforeEach
    void setup() {
        when(profiles.find("tenant", "routing-v1")).thenReturn(Optional.of(profile("merchant", List.of(rule("primary")))));
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void selectionUsesOriginalPayeeChannelMethodAndFrozenAccessModes() {
        var command = PaymentFixtures.command();
        var mode = command.admittedTerms().terms().capabilities().iterator().next().accessMode();
        var identity = new WechatAccountIdentity("app", "mchid", null, mode == AccessMode.DIRECT ? null : "sub-mchid");
        var binding = new ChannelBindingRevision("revision", "merchant", command.choice().channel(), mode,
                "a".repeat(64), "credential", "v1", identity, Set.of(command.choice().method()));
        var head = new ChannelMerchantBinding("binding", "merchant", "primary", binding.channel(), mode,
                ChannelBindingState.ACTIVE, binding.id(), 1, 100, 1);
        var revision = new StoredChannelBindingRevision(binding, head.id(), 1, "b".repeat(64), "pay-ops", LocalDateTime.of(2026, 10, 22, 12, 0));
        when(bindings.lockActive("tenant", "merchant", binding.channel(), Set.of(mode), binding.capabilities().iterator().next()))
                .thenReturn(List.of(new RoutableChannelBinding(head, revision)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(binding, selector.select("tenant", command));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(bindings).lockActive("tenant", command.payeeMerchantId(), command.choice().channel(), Set.of(mode), command.choice().method());
    }

    @Test
    void missingRouteDoesNotInventADefaultMerchantOrBinding() {
        var command = PaymentFixtures.command();
        when(bindings.lockActive(anyString(), anyString(), any(), anySet(), any())).thenReturn(List.of());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(CHANNEL_BINDING_UNAVAILABLE, assertThrows(CommonException.class,
                    () -> selector.select("tenant", command)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void selectionRequiresTheCallersLocalTransactionBeforeTakingRouteLocks() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> selector.select("tenant", PaymentFixtures.command())).getResultCode());
        verifyNoInteractions(bindings, profiles);
    }

    @Test
    void missingProfileCannotFallBackToAnArbitraryActiveBinding() {
        when(profiles.find("tenant", "routing-v1")).thenReturn(Optional.empty());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertSame(ROUTING_PROFILE_UNAVAILABLE, assertThrows(CommonException.class,
                () -> selector.select("tenant", PaymentFixtures.command())).getResultCode());
        verifyNoInteractions(bindings);
    }

    @Test
    void anotherMerchantsProfileCannotRouteEvenWhenBindingsWouldMatch() {
        when(profiles.find("tenant", "routing-v1")).thenReturn(Optional.of(profile("other-merchant", List.of(rule("primary")))));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertSame(ROUTING_PROFILE_UNAVAILABLE, assertThrows(CommonException.class,
                () -> selector.select("tenant", PaymentFixtures.command())).getResultCode());
        verifyNoInteractions(bindings);
    }

    @Test
    void profileOrderWinsOverGlobalBindingPriority() {
        var first = candidate("first", 999);
        var second = candidate("second", 1);
        when(profiles.find("tenant", "routing-v1")).thenReturn(Optional.of(profile("merchant", List.of(rule("first"), rule("second")))));
        when(bindings.lockActive(anyString(), anyString(), any(), anySet(), any())).thenReturn(List.of(second, first));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals(first.revision().binding(), selector.select("tenant", PaymentFixtures.command()));
    }

    @Test
    void missingFirstCandidateMayUseOnlyAnExplicitFollowingCandidate() {
        var second = candidate("second", 100);
        when(profiles.find("tenant", "routing-v1")).thenReturn(Optional.of(profile("merchant", List.of(rule("first"), rule("second")))));
        when(bindings.lockActive(anyString(), anyString(), any(), anySet(), any())).thenReturn(List.of(second));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals(second.revision().binding(), selector.select("tenant", PaymentFixtures.command()));
    }

    @Test
    void activeUnlistedBindingCannotBeUsedAsImplicitFallback() {
        when(bindings.lockActive(anyString(), anyString(), any(), anySet(), any())).thenReturn(List.of(candidate("unlisted", 0)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertSame(CHANNEL_BINDING_UNAVAILABLE, assertThrows(CommonException.class,
                () -> selector.select("tenant", PaymentFixtures.command())).getResultCode());
    }

    @Test
    void profileCannotExpandTheFrozenContractAccessMode() {
        var rule = new RoutingRule("provider", Channel.WECHAT, AccessMode.SERVICE_PROVIDER, PaymentMethod.H5);
        when(profiles.find("tenant", "routing-v1")).thenReturn(Optional.of(profile("merchant", List.of(rule))));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertSame(UNSUPPORTED_CAPABILITY, assertThrows(CommonException.class,
                () -> selector.select("tenant", PaymentFixtures.command())).getResultCode());
        verifyNoInteractions(bindings);
    }
}
