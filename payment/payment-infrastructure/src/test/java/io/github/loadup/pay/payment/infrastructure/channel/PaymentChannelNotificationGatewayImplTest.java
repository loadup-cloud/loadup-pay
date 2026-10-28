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
package io.github.loadup.pay.payment.infrastructure.channel;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelNotificationFacade;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PaymentChannelNotificationGatewayImplTest {
    private final ChannelNotificationFacade facade = mock(ChannelNotificationFacade.class);
    private final ChannelBindingGateway bindings = mock(ChannelBindingGateway.class);
    private final PaymentChannelNotificationGatewayImpl gateway = new PaymentChannelNotificationGatewayImpl(facade, bindings);
    private final ChannelNotificationInput input = new ChannelNotificationInput("application/json", Map.of(), "{}".getBytes(StandardCharsets.UTF_8));
    private final ChannelBindingRevision original = new ChannelBindingRevision("old-revision", "merchant", Channel.WECHAT,
            AccessMode.DIRECT, "a".repeat(64), "credential", "v1", new WechatAccountIdentity("app", "1900000001", null, null), Set.of(PaymentMethod.JSAPI));

    @Test
    void identifiesEncryptedNotificationUsingTrustedTenantAndOriginalEndpointRevision() {
        when(bindings.findRevision("tenant", "old-revision")).thenReturn(Optional.of(original));
        var expected = new ChannelNotificationHint("original-order");
        when(facade.identify(new ChannelExecutionContext("tenant", original), input)).thenReturn(expected);
        assertEquals(expected, TenantUtil.callWithTenant("tenant", () -> gateway.identify(Channel.WECHAT, "old-revision", input)));
        verify(bindings).findRevision("tenant", "old-revision");
        verify(facade).identify(new ChannelExecutionContext("tenant", original), input);
        verifyNoMoreInteractions(bindings, facade);
    }

    @Test
    void refusesMissingOrWrongChannelBindingBeforeDecryptingOrCallingProvider() {
        when(bindings.findRevision("tenant", "missing")).thenReturn(Optional.empty());
        when(bindings.findRevision("tenant", "old-revision")).thenReturn(Optional.of(original));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> gateway.identify(Channel.WECHAT, "missing", input));
            assertThrows(CommonException.class, () -> gateway.identify(Channel.ALIPAY, "old-revision", input));
        });
        verifyNoInteractions(facade);
    }

    @Test
    void refusesLocalTransactionBeforeAnyCredentialOrBindingAccess() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class,
                    () -> gateway.identify(Channel.WECHAT, "old-revision", input)));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(bindings, facade);
    }
}
