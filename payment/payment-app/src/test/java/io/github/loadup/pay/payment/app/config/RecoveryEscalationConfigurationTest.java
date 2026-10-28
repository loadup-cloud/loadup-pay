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

import io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryEscalationConfigurationTest {
    @Test
    void defaultAndExplicitThresholdsAreTheActualPolicyBean() {
        try (var context = new AnnotationConfigApplicationContext(RecoveryEscalationConfiguration.class)) {
            assertEquals(new RecoveryEscalationPolicy(900, 86400), context.getBean(RecoveryEscalationPolicy.class));
        }
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("escalation", Map.<String, Object>of(
                    "loadup.modules.payment.recovery-escalation.review-after-seconds", 600,
                    "loadup.modules.payment.recovery-escalation.urgent-after-seconds", 3600)));
            context.register(RecoveryEscalationConfiguration.class);
            context.refresh();
            assertEquals(new RecoveryEscalationPolicy(600, 3600), context.getBean(RecoveryEscalationPolicy.class));
        }
    }

    @Test
    void reversedThresholdsFailAssemblyRatherThanReportingMisleadingLevels() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("escalation", Map.<String, Object>of(
                    "loadup.modules.payment.recovery-escalation.review-after-seconds", 900,
                    "loadup.modules.payment.recovery-escalation.urgent-after-seconds", 600)));
            context.register(RecoveryEscalationConfiguration.class);
            assertThrows(BeanCreationException.class, context::refresh);
        }
    }
}
