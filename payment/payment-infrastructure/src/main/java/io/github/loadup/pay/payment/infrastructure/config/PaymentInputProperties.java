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
package io.github.loadup.pay.payment.infrastructure.config;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.kms.KmsKeyRef;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@ConfigurationProperties("loadup.modules.payment.input")
public record PaymentInputProperties(@DefaultValue("120") int lifetimeSeconds,
                                     @DefaultValue("payment-input") String encryptionKey, String activeFingerprintKeyId,
                                     @DiagnosticHidden Map<String, String> fingerprintKeys) {
    public PaymentInputProperties {
        if (lifetimeSeconds < 1 || lifetimeSeconds > 600)
            throw new IllegalStateException("Payment input lifetime must be between 1 and 600 seconds");
        KmsKeyRef.latest(encryptionKey);
        DomainChecks.id(activeFingerprintKeyId);
        if (fingerprintKeys == null || !fingerprintKeys.containsKey(activeFingerprintKeyId))
            throw new IllegalStateException("Active payment input fingerprint key must be configured");
        fingerprintKeys = Map.copyOf(fingerprintKeys);
        fingerprintKeys.forEach((id, key) -> {
            DomainChecks.id(id);
            if (key.isBlank() || key.getBytes(StandardCharsets.UTF_8).length < 32)
                throw new IllegalStateException("Payment input fingerprint keys require at least 32 random UTF-8 bytes");
        });
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
