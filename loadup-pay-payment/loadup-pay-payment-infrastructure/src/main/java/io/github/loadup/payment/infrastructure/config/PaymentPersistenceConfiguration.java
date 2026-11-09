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
package io.github.loadup.payment.infrastructure.config;

import io.github.loadup.components.http.HttpClientDefaults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentInputProperties.class)
public class PaymentPersistenceConfiguration {
    @Bean
    public PaymentLimitProperties paymentLimitProperties(Environment environment) {
        return HttpClientDefaults.bind(
                environment, "loadup.modules.payment.limitcenter", "limitcenter", PaymentLimitProperties.class);
    }

    @Bean
    public PaymentAcquireProperties paymentAcquireProperties(Environment environment) {
        return HttpClientDefaults.bind(
                environment, "loadup.modules.payment.acquire", "acquire", PaymentAcquireProperties.class);
    }

    @Bean
    public PaymentRealtimeProperties paymentRealtimeProperties(Environment environment) {
        return HttpClientDefaults.bind(
                environment, "loadup.modules.payment.realtime", "realtime", PaymentRealtimeProperties.class);
    }
}
