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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlipayWapProtocolClientTest {
    private final HttpTemplate http = mock(HttpTemplate.class);
    private final AlipayCredentialReader credentials = mock(AlipayCredentialReader.class);
    private final AlipayProtocolCodec codec = mock(AlipayProtocolCodec.class);
    private final AlipayProtocolClient client = new AlipayProtocolClient(http, credentials, codec);
    private final URI gateway = URI.create("https://openapi.alipay.com/gateway.do");
    private final String business = "{\"product_code\":\"QUICK_WAP_WAY\"}";

    private static ChannelBindingRevision binding(AccessMode mode) {
        return new ChannelBindingRevision("binding", "merchant", Channel.ALIPAY, mode, "a".repeat(64), "credential", "v1",
                new AlipayAccountIdentity("app", "seller", null,
                        mode == AccessMode.DIRECT ? null : "authorization", mode == AccessMode.DIRECT ? null : "v1"),
                Set.of(PaymentMethod.H5));
    }

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void pagePreparationSignsTheOriginalBodyWithoutCallingHttpOrVerifyingAnInventedResponse() {
        var binding = binding(AccessMode.DIRECT);
        var resolved = new AlipayCredentials("tenant", "app", "seller", null, null, null);
        var options = Map.of("notify_url", "https://callbacks.example.test/alipay");
        var signed = new AlipaySignedRequest(AlipayMethod.WAP_PAY, Map.of("method", "alipay.trade.wap.pay", "sign", "a+b="));
        when(credentials.read("tenant", binding)).thenReturn(resolved);
        when(codec.sign(AlipayMethod.WAP_PAY, resolved, business, options)).thenReturn(signed);

        assertEquals(gateway + "?" + signed.formBody(), client.directWapUrl("tenant", binding, gateway, business, options));
        verify(codec).sign(AlipayMethod.WAP_PAY, resolved, business, options);
        verifyNoInteractions(http);
        verify(codec, never()).verify(any(), any(), any());
        assertThrows(CommonException.class, () -> client.call("tenant", binding, AlipayMethod.WAP_PAY, business, options));
    }

    @Test
    void delegatedModeForeignGatewayQueryAndTransactionAreRejectedBeforeCredentialAccess() {
        assertThrows(CommonException.class, () -> client.directWapUrl("tenant", binding(AccessMode.SERVICE_PROVIDER), gateway, business, Map.of()));
        for (var invalid : Set.of("http://openapi.alipay.com/gateway.do", "https://attacker.test/gateway.do",
                "https://openapi.alipay.com/gateway.do?app_auth_token=secret", "https://openapi.alipay.com/other")) {
            assertThrows(CommonException.class, () -> client.directWapUrl("tenant", binding(AccessMode.DIRECT), URI.create(invalid), business, Map.of()));
        }
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(CommonException.class, () -> client.directWapUrl("tenant", binding(AccessMode.DIRECT), gateway, business, Map.of()));
        verifyNoInteractions(credentials, codec, http);
    }

    @Test
    void unexpectedAuthorizationOnDirectCredentialsIsNeverSignedOrReturned() {
        var binding = binding(AccessMode.DIRECT);
        when(credentials.read("tenant", binding)).thenReturn(new AlipayCredentials("tenant", "app", "seller", null, null, "secret"));
        assertThrows(CommonException.class, () -> client.directWapUrl("tenant", binding, gateway, business, Map.of()));
        verifyNoInteractions(codec, http);
    }
}
