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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.app.model.ChannelCallbackEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("loadup.modules.payment.channel-callback")
public record PaymentChannelCallbackProperties(Map<String, ChannelCallbackEndpoint> endpoints) {
    public PaymentChannelCallbackProperties {
        if (endpoints == null || endpoints.isEmpty()) {
            endpoints = Map.of();
        } else {
            endpoints.forEach((endpointRef, endpoint) -> {
                io.github.loadup.commons.error.DomainViolation.require(validEndpointRef(endpointRef) && endpoint != null,
                        io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT);
            });
            endpoints = Map.copyOf(endpoints);
        }
    }

    public static boolean validEndpointRef(String endpointRef) {
        return endpointRef != null && endpointRef.matches("[A-Za-z0-9_-]{1,64}");
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
