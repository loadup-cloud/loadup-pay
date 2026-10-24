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
package io.github.loadup.pay.acquire.infrastructure.config;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.signature.request.RequestSigningIdentity;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Tenant/merchant-owned immutable catalog references; changing a target requires a new reference. */
@ConfigurationProperties("loadup.modules.acquire.merchant-notifications")
public record MerchantNotificationProperties(Map<String, Map<String, Target>> targets,
        Map<String, Map<String, Signing>> signingIdentities) {
    public MerchantNotificationProperties {
        targets = immutable(targets); signingIdentities = immutable(signingIdentities);
    }
    private static <T> Map<String, Map<String, T>> immutable(Map<String, Map<String, T>> values) {
        if (values == null) return Map.of();
        return values.entrySet().stream().collect(Collectors.toUnmodifiableMap(e -> DomainChecks.id(e.getKey()), e -> {
            e.getValue().keySet().forEach(key -> DomainChecks.text(key, 128));
            return Map.copyOf(e.getValue());
        }));
    }
    public record Target(String payeeMerchantId, String client, String operation, @DiagnosticHidden URI uri) {
        public Target {
            DomainChecks.id(payeeMerchantId); DomainChecks.id(client); DomainChecks.id(operation);
            if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || uri.getRawQuery() != null || uri.getRawPath().isBlank() || uri.toASCIIString().length() > 2048
                    || (uri.getPort() != -1 && uri.getPort() != 443)) throw new IllegalArgumentException("Invalid approved notification target");
        }
        @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
    }
    public record Signing(String payeeMerchantId, @DiagnosticHidden RequestSigningIdentity identity) {
        public Signing { DomainChecks.id(payeeMerchantId); Objects.requireNonNull(identity); }
        @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
