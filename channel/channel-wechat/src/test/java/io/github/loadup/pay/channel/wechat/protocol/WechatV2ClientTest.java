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
package io.github.loadup.pay.channel.wechat.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WechatV2ClientTest {
    private final HttpTemplate http = mock(HttpTemplate.class);
    private final WechatCredentialReader credentials = mock(WechatCredentialReader.class);
    private final WechatV2Codec codec = new WechatV2Codec();
    private final WechatV2Client client = new WechatV2Client(http, credentials, codec);
    private final ChannelBindingRevision binding = new ChannelBindingRevision("binding", "merchant", Channel.WECHAT, AccessMode.DIRECT,
            "a".repeat(64), "credential", "v1", new WechatAccountIdentity("wx-app", "1900000001", null, null), Set.of(PaymentMethod.PAYMENT_CODE));
    private final Map<String, String> request = Map.of("appid", "wx-app", "mch_id", "1900000001", "sign_type", "HMAC-SHA256");

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void verifiedResponseUsesExactV2KeyAndErasesDecryptedBytesAfterTheOneHttpCall() {
        byte[] key = "192006250b4c09247ec02edce69f6a2d".getBytes(StandardCharsets.UTF_8);
        var body = codec.encode(Map.of("return_code", "SUCCESS", "result_code", "FAIL", "err_code", "USERPAYING", "sign_type", "HMAC-SHA256"), key);
        when(credentials.apiV2Key("tenant", binding)).thenReturn(key);
        when(http.exchange(eq("wechat-v2"), eq("micropay"), any())).thenReturn(ResponseEntity.ok(body.getBytes(StandardCharsets.UTF_8)));
        var result = TenantUtil.callWithTenant("tenant", () -> client.call("tenant", binding, WechatV2Method.MICROPAY, request));
        assertEquals("USERPAYING", result.fields().get("err_code"));
        assertArrayEquals(new byte[32], key);
        verify(http, times(1)).exchange(eq("wechat-v2"), eq("micropay"), any());
        verify(credentials, never()).apiV3Key(anyString(), any());
    }

    @Test
    void failedTransportStillErasesKeyAndDoesNotRetry() {
        byte[] key = "192006250b4c09247ec02edce69f6a2d".getBytes(StandardCharsets.UTF_8);
        when(credentials.apiV2Key("tenant", binding)).thenReturn(key);
        when(http.exchange(eq("wechat-v2"), eq("micropay"), any())).thenThrow(new IllegalStateException("synthetic transport failure"));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> client.call("tenant", binding, WechatV2Method.MICROPAY, request)));
        assertArrayEquals(new byte[32], key);
        verify(http, times(1)).exchange(eq("wechat-v2"), eq("micropay"), any());
    }

    @Test
    void wrongTenantAccountAndAmbientTransactionNeverReadSecretsOrCallHttp() {
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> client.call("tenant", binding, WechatV2Method.MICROPAY, request)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> client.call("tenant", binding, WechatV2Method.MICROPAY,
                Map.of("appid", "wx-app", "mch_id", "wrong"))));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(CommonException.class, () -> client.call("tenant", binding, WechatV2Method.MICROPAY, request));
        verifyNoInteractions(credentials, http);
    }
}
