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
package io.github.loadup.pay.limitcenter.infrastructure.config;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@ConfigurationProperties("loadup.modules.limitcenter.permit")
public record LimitPermitProperties(
        String activeKeyId, @DiagnosticHidden Map<String, String> keys) {
    public LimitPermitProperties {
        DomainChecks.id(activeKeyId);
        if (keys == null || !keys.containsKey(activeKeyId))
            throw new IllegalStateException("Active limit permit key must be configured");
        keys = Map.copyOf(keys);
        keys.forEach((id, secret) -> {
            DomainChecks.id(id);
            if (secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32)
                throw new IllegalStateException("Limit permit keys require at least 32 random UTF-8 bytes");
        });
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
