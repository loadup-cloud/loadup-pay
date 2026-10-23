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

import io.github.loadup.pay.payment.domain.gateway.RiskConsultGateway;
import io.github.loadup.pay.payment.domain.gateway.RiskOutcomeGateway;
import io.github.loadup.pay.payment.domain.model.RiskConsultation;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.RiskDecisionType;
import io.github.loadup.pay.payment.domain.model.RiskOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentRiskConfigurationTest {
    @Test
    void disconnectedPolicySuppliesBothConsultationAndOutcomeRoles() {
        try (var context = new AnnotationConfigApplicationContext(PaymentRiskConfiguration.class)) {
            assertSame(context.getBean(RiskConsultGateway.class), context.getBean(RiskOutcomeGateway.class));
        }
    }

    @Test
    void configuredProviderOwnsBothRolesWithoutASecondDefaultProvider() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var provider = new ExternalRisk();
            context.registerBean("externalRisk", ExternalRisk.class, () -> provider);
            context.register(PaymentRiskConfiguration.class);
            context.refresh();
            assertSame(provider, context.getBean(RiskConsultGateway.class));
            assertSame(provider, context.getBean(RiskOutcomeGateway.class));
            assertFalse(context.containsBean("defaultRiskGateway"));
        }
    }

    @Test
    void partialExternalConfigurationDoesNotSilentlyMixInDisconnectedResults() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("externalConsult", RiskConsultGateway.class,
                    () -> consultation -> new RiskDecision(RiskDecisionType.ALLOW, "EXTERNAL_ALLOW", "consultation"));
            context.register(PaymentRiskConfiguration.class);
            context.refresh();
            assertTrue(context.getBeansOfType(RiskOutcomeGateway.class).isEmpty());
            assertFalse(context.containsBean("defaultRiskGateway"));
        }
    }

    static class ExternalRisk implements RiskConsultGateway, RiskOutcomeGateway {
        @Override
        public RiskDecision consult(RiskConsultation value) {
            return new RiskDecision(RiskDecisionType.ALLOW, "EXTERNAL_ALLOW", "consultation");
        }

        @Override
        public void confirm(RiskOutcome value) {
        }
    }
}
