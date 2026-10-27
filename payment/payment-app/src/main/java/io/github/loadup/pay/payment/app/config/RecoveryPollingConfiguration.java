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
package io.github.loadup.pay.payment.app.config;

import io.github.loadup.pay.payment.domain.policy.RecoveryPollingPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RecoveryPollingConfiguration {
    @Bean
    public RecoveryPollingPolicy recoveryPollingPolicy(
            @Value("${loadup.modules.payment.recovery-polling.initial-delay-seconds:30}") long initialDelaySeconds,
            @Value("${loadup.modules.payment.recovery-polling.maximum-delay-seconds:1800}") long maximumDelaySeconds,
            @Value("${loadup.modules.payment.recovery-polling.backoff-every-seconds:900}") long backoffEverySeconds,
            @Value("${loadup.modules.payment.recovery-polling.jitter-percent:20}") int jitterPercent) {
        return new RecoveryPollingPolicy(initialDelaySeconds, maximumDelaySeconds, backoffEverySeconds, jitterPercent);
    }
}
