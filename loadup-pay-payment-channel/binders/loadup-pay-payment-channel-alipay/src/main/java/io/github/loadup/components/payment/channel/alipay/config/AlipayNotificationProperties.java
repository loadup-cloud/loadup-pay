/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.alipay.config;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trusted server callback URLs by immutable binding revision; never supplied by a merchant request.
 */
@ConfigurationProperties("loadup.payment.channel.alipay.notifications")
public record AlipayNotificationProperties(Map<String, URI> notifyUrls) {
    public AlipayNotificationProperties {
        notifyUrls = notifyUrls == null ? Map.of() : Map.copyOf(notifyUrls);
        for (var binding : notifyUrls.entrySet()) {
            DomainChecks.id(binding.getKey());
            require(safeKey(binding.getKey()), INVALID_ARGUMENT);
            var uri = binding.getValue();
            require(
                    uri != null
                            && "https".equals(uri.getScheme())
                            && uri.getHost() != null
                            && uri.getRawUserInfo() == null
                            && uri.getRawFragment() == null
                            && uri.getRawQuery() == null
                            && uri.getRawPath() != null
                            && !uri.getRawPath().isBlank()
                            && uri.toASCIIString().length() <= 2048,
                    INVALID_ARGUMENT);
        }
    }

    private static boolean safeKey(String value) {
        // Frozen binding identifiers are used as configuration keys; reject path-like values.
        return value.matches("[A-Za-z0-9_][A-Za-z0-9._:-]{0,63}");
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
