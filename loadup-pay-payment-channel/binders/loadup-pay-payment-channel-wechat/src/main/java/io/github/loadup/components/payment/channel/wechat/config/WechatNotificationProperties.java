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
package io.github.loadup.components.payment.channel.wechat.config;

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
@ConfigurationProperties("loadup.payment.channel.wechat.notifications")
public record WechatNotificationProperties(Map<String, URI> notifyUrls, Map<String, URI> refundNotifyUrls) {
    public WechatNotificationProperties {
        notifyUrls = immutable(notifyUrls);
        refundNotifyUrls = immutable(refundNotifyUrls);
    }

    private static Map<String, URI> immutable(Map<String, URI> values) {
        var urls = values == null ? Map.<String, URI>of() : Map.copyOf(values);
        for (var binding : urls.entrySet()) {
            DomainChecks.id(binding.getKey());
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
        return urls;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
