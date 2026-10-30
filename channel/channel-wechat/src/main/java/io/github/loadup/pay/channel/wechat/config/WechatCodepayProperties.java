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
package io.github.loadup.pay.channel.wechat.config;

import com.google.common.net.InetAddresses;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.stream.Collectors;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Trusted API-calling machine IP by tenant and binding revision, never a browser forwarding header.
 */
@ConfigurationProperties("loadup.pay.channel.wechat.codepay")
public record WechatCodepayProperties(Map<String, Map<String, String>> serverIps) {
    public WechatCodepayProperties {
        serverIps = serverIps == null ? Map.of() : serverIps.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        for (var tenant : serverIps.entrySet()) {
            DomainChecks.id(tenant.getKey());
            for (var binding : tenant.getValue().entrySet()) {
                DomainChecks.id(binding.getKey());
                require(validIp(binding.getValue()), INVALID_ARGUMENT);
            }
        }
    }

    private static boolean validIp(String value) {
        if (value == null || value.length() > 64 || !InetAddresses.isInetAddress(value)) {
            return false;
        }
        var address = InetAddresses.forString(value);
        return !address.isAnyLocalAddress() && !address.isMulticastAddress() && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
