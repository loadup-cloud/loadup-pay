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

import io.github.loadup.pay.payment.app.port.ChannelCallbackEndpointResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentChannelCallbackProperties.class)
public class PaymentChannelCallbackConfiguration {
    @Bean
    public ChannelCallbackEndpointResolver channelCallbackEndpointResolver(PaymentChannelCallbackProperties properties) {
        return endpointRef -> {
            if (!PaymentChannelCallbackProperties.validEndpointRef(endpointRef)) return Optional.empty();
            return Optional.ofNullable(properties.endpoints().get(endpointRef));
        };
    }
}
