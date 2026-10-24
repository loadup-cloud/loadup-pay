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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.components.kms.KmsCiphertext;

/** Immutable mounted descriptor. Merchant private keys remain in KMS. */
public record WechatCredentialDocument(String tenantId, String reference, String version, String appId, String merchantId,
        String merchantSerial, String signingKeyName, int signingKeyVersion, String platformKeyId,
        @DiagnosticHidden String platformPublicKey, @DiagnosticHidden KmsCiphertext apiV3Key) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
