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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.components.kms.KmsCiphertext;

/**
 * The merchant's delegated authorization is a separate versioned resource.
 */
public record AlipayAuthorizationDocument(String tenantId, String reference, String version, String appId,
                                          String sellerId,
                                          @DiagnosticHidden KmsCiphertext token) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
