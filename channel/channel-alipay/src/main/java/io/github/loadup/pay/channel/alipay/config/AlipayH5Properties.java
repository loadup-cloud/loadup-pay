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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Explicit environment gateway and trusted return destinations; no merchant-supplied redirect.
 */
@ConfigurationProperties("loadup.pay.channel.alipay.h5")
public record AlipayH5Properties(URI gateway, Map<String, Map<String, URI>> returnUrls) {
    public AlipayH5Properties {
        require(gateway == null || AlipayH5ChannelAction.isGateway(gateway) && gateway.getRawQuery() == null,
                INVALID_ARGUMENT);
        returnUrls = returnUrls == null ? Map.of() : returnUrls.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        for (var tenant : returnUrls.entrySet()) {
            DomainChecks.id(tenant.getKey());
            for (var binding : tenant.getValue().entrySet()) {
                DomainChecks.id(binding.getKey());
                var uri = binding.getValue();
                require(uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null
                        && uri.getRawUserInfo() == null && uri.getRawFragment() == null && uri.getRawQuery() == null
                        && uri.toASCIIString().length() <= 256, INVALID_ARGUMENT);
            }
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
