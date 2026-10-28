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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryPollingConfigurationTest {
    @Test
    void defaultConfigurationProvidesTheSharedCollectionAndRefundPolicy() {
        try (var context = new AnnotationConfigApplicationContext(RecoveryPollingConfiguration.class)) {
            assertEquals(new RecoveryPollingPolicy(30, 1800, 900, 20), context.getBean(RecoveryPollingPolicy.class));
        }
    }

    @Test
    void explicitOperationalConfigurationReplacesTheDefaults() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("polling", Map.<String, Object>of(
                    "loadup.modules.payment.recovery-polling.initial-delay-seconds", 60,
                    "loadup.modules.payment.recovery-polling.maximum-delay-seconds", 3600,
                    "loadup.modules.payment.recovery-polling.backoff-every-seconds", 300,
                    "loadup.modules.payment.recovery-polling.jitter-percent", 10)));
            context.register(RecoveryPollingConfiguration.class);
            context.refresh();
            assertEquals(new RecoveryPollingPolicy(60, 3600, 300, 10), context.getBean(RecoveryPollingPolicy.class));
        }
    }

    @Test
    void anInvalidCeilingFailsAssemblyInsteadOfQuietlyDisablingBackoff() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("polling", Map.<String, Object>of(
                    "loadup.modules.payment.recovery-polling.initial-delay-seconds", 60,
                    "loadup.modules.payment.recovery-polling.maximum-delay-seconds", 30)));
            context.register(RecoveryPollingConfiguration.class);
            assertThrows(BeanCreationException.class, context::refresh);
        }
    }
}
