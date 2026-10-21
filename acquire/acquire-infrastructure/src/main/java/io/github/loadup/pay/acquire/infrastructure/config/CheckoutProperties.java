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

import io.github.loadup.commons.json.*;
import io.github.loadup.pay.common.DomainChecks;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loadup.modules.acquire.checkout")
public record CheckoutProperties(
        URI pageUrl,
        String keyRef,
        String activeKeyVersion,
        @DiagnosticHidden Map<String, String> keys) {
    public CheckoutProperties {
        DomainChecks.text(keyRef, 128);
        DomainChecks.id(activeKeyVersion);
        if (pageUrl != null) DomainChecks.text(pageUrl.toASCIIString(), 512);
        if (pageUrl == null
                || pageUrl.getHost() == null
                || pageUrl.getRawUserInfo() != null
                || pageUrl.getRawQuery() != null
                || pageUrl.getRawFragment() != null
                || pageUrl.getRawAuthority() == null
                || !("https".equals(pageUrl.getScheme())
                        || "http".equals(pageUrl.getScheme())
                                && Set.of("localhost", "127.0.0.1", "[::1]").contains(pageUrl.getHost()))
                || !"/checkout".equals(pageUrl.getPath()))
            throw new IllegalStateException("Checkout page requires an HTTPS /checkout URL (HTTP only on loopback)");
        if (keys == null || !keys.containsKey(activeKeyVersion))
            throw new IllegalStateException("Active checkout key must be configured");
        keys = Map.copyOf(keys);
        keys.forEach((version, secret) -> {
            DomainChecks.id(version);
            if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32)
                throw new IllegalStateException("Checkout keys require at least 32 random UTF-8 bytes");
        });
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
