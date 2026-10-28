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
package io.github.loadup.pay.channel.alipay.config;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

@ConfigurationProperties("loadup.pay.channel.alipay")
public record AlipayProperties(boolean enabled,
                               @DiagnosticHidden Map<String, Map<String, Path>> credentials,
                               @DiagnosticHidden Map<String, Map<String, Path>> authorizations) {
    public AlipayProperties {
        credentials = copy(credentials);
        authorizations = copy(authorizations);
    }

    private static Map<String, Map<String, Path>> copy(Map<String, Map<String, Path>> source) {
        return source == null ? Map.of() : source.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
