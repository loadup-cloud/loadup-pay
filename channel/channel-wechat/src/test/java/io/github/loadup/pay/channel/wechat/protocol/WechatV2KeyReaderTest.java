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
import io.github.loadup.commons.payment.*;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.kms.*;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.wechat.config.WechatProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatV2KeyReaderTest {
    @TempDir Path directory;
    private final KmsTemplate kms = mock(KmsTemplate.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final KmsCiphertext v2 = new KmsCiphertext("wechat-v2", 7, "vault:v7:AQ==");
    private final KmsCiphertext v3 = new KmsCiphertext("wechat-v3", 2, "vault:v2:Ag==");
    private final ChannelBindingRevision binding = new ChannelBindingRevision("binding", "merchant", Channel.WECHAT, AccessMode.DIRECT,
            "a".repeat(64), "credential", "v1", new WechatAccountIdentity("wx-app", "1900000001", null, null), Set.of(PaymentMethod.PAYMENT_CODE));

    @Test
    void onlyTheFrozenV2CiphertextIsDecryptedAndCallerReceivesOwnedBytes() throws Exception {
        var reader = reader(v2);
        byte[] value = "192006250b4c09247ec02edce69f6a2d".getBytes(StandardCharsets.UTF_8);
        when(kms.decrypt(new KmsKeyRef("wechat-v2", 7), v2)).thenReturn(value);
        var actual = TenantUtil.callWithTenant("tenant", () -> reader.apiV2Key("tenant", binding));
        assertSame(value, actual);
        verify(kms).decrypt(new KmsKeyRef("wechat-v2", 7), v2);
        verify(kms, never()).decrypt(new KmsKeyRef("wechat-v3", 2), v3);
    }

    @Test
    void absentV2KeyCannotFallBackToV3AndMalformedDecryptedKeyIsWiped() throws Exception {
        var absent = reader(null);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> absent.apiV2Key("tenant", binding)));
        verifyNoInteractions(kms);
        var configured = reader(v2);
        byte[] malformed = new byte[32]; malformed[0] = 1;
        when(kms.decrypt(new KmsKeyRef("wechat-v2", 7), v2)).thenReturn(malformed);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> configured.apiV2Key("tenant", binding)));
        assertArrayEquals(new byte[32], malformed);
    }

    private WechatCredentialReader reader(KmsCiphertext apiV2Key) throws Exception {
        var path = directory.resolve("credential.json");
        var document = new WechatCredentialDocument("tenant", "credential", "v1", "wx-app", "1900000001", "1234ABCD",
                "signer", 3, "PUB_KEY_ID_synthetic", "unused-for-symmetric-key-read", v3, apiV2Key);
        Files.writeString(path, json.writeValueAsString(document));
        return new WechatCredentialReader(new WechatProperties(true, Map.of("credential", Map.of("v1", path))), json, kms);
    }
}
