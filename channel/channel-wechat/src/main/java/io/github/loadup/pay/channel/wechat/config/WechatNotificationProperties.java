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

import io.github.loadup.commons.json.ToStringAsJson;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Trusted server callback URLs by tenant and immutable binding revision; never supplied by a merchant request. */
@ConfigurationProperties("loadup.pay.channel.wechat.notifications")
public record WechatNotificationProperties(Map<String, Map<String, URI>> notifyUrls, Map<String, Map<String, URI>> refundNotifyUrls) {
    public WechatNotificationProperties {
        notifyUrls = immutable(notifyUrls);
        refundNotifyUrls = immutable(refundNotifyUrls);
    }
    private static Map<String, Map<String, URI>> immutable(Map<String, Map<String, URI>> values) {
        var urls = values == null ? Map.<String, Map<String, URI>>of() : values.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        for (var tenant : urls.entrySet()) {
            io.github.loadup.commons.validation.DomainChecks.id(tenant.getKey());
            for (var binding : tenant.getValue().entrySet()) {
                io.github.loadup.commons.validation.DomainChecks.id(binding.getKey());
                var uri = binding.getValue();
                require(uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null
                        && uri.getRawUserInfo() == null && uri.getRawFragment() == null && uri.getRawQuery() == null
                        && uri.getRawPath() != null && !uri.getRawPath().isBlank() && uri.toASCIIString().length() <= 2048,
                        INVALID_ARGUMENT);
            }
        }
        return urls;
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
