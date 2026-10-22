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
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRouteSelectorTest {
    final ChannelBindingGateway bindings = mock(ChannelBindingGateway.class);
    final PaymentRouteSelector selector = new PaymentRouteSelector(bindings);
    @Test void selectionUsesOriginalPayeeChannelMethodAndFrozenAccessModes() {
        var command = PaymentFixtures.command();
        var mode = command.admittedTerms().terms().capabilities().iterator().next().accessMode();
        var identity = new WechatAccountIdentity("app", "mchid", null, mode == AccessMode.DIRECT ? null : "sub-mchid");
        var binding = new ChannelBindingRevision("revision", "merchant", command.choice().channel(), mode,
                "a".repeat(64), "credential", "v1", identity, Set.of(command.choice().method()));
        var head = new ChannelMerchantBinding("binding", "merchant", "primary", binding.channel(), mode,
                ChannelBindingState.ACTIVE, binding.id(), 1, 100, 1);
        var revision = new StoredChannelBindingRevision(binding, head.id(), 1, "b".repeat(64), "pay-ops", LocalDateTime.of(2026,10,22,12,0));
        when(bindings.lockActive("tenant", "merchant", binding.channel(), Set.of(mode), binding.capabilities().iterator().next()))
                .thenReturn(List.of(new RoutableChannelBinding(head, revision)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertEquals(binding, selector.select("tenant", command)); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(bindings).lockActive("tenant", command.payeeMerchantId(), command.choice().channel(), Set.of(mode), command.choice().method());
    }
    @Test void missingRouteDoesNotInventADefaultMerchantOrBinding() {
        var command = PaymentFixtures.command();
        when(bindings.lockActive(anyString(), anyString(), any(), anySet(), any())).thenReturn(List.of());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(CHANNEL_BINDING_UNAVAILABLE, assertThrows(CommonException.class,
                    () -> selector.select("tenant", command)).getResultCode());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    @Test void selectionRequiresTheCallersLocalTransactionBeforeTakingRouteLocks() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> selector.select("tenant", PaymentFixtures.command())).getResultCode());
        verifyNoInteractions(bindings);
    }
}
