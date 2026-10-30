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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.pay.payment.app.converter.PaymentOutcomeDeliveryConverter;
import io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter;
import io.github.loadup.pay.payment.domain.gateway.RiskOutcomeGateway;
import org.springframework.stereotype.Component;

@Component
public class PaymentRiskOutcomeDelivery {
    private final PaymentOutcomeDeliverySupport support;
    private final PaymentOutcomeDeliveryConverter converter;
    private final PaymentSubmissionConverter consultations;
    private final RiskOutcomeGateway risk;

    public PaymentRiskOutcomeDelivery(PaymentOutcomeDeliverySupport support, PaymentOutcomeDeliveryConverter converter,
                                      PaymentSubmissionConverter consultations, RiskOutcomeGateway risk) {
        this.support = support;
        this.converter = converter;
        this.consultations = consultations;
        this.risk = risk;
    }

    public void deliver(OutboxEvent event) {
        var snapshot = support.load(event, PaymentEvents.RISK);
        // A local close may precede any consultation. Never invent DEFAULT_ALLOW/a consultation for that case.
        if (snapshot.attempt() == null || snapshot.attempt().riskDecision() == null) {
            return;
        }
        var outcome = converter.toRisk(snapshot.tenantId(), snapshot.envelope().businessEventId(),
                consultations.toConsultation(snapshot.payment(), snapshot.attempt()), snapshot.attempt().riskDecision(),
                snapshot.payment().payment().state(), snapshot.payment().payment().version(), snapshot.payment().terminalEvidenceId());
        risk.confirm(outcome); // Outside the local transaction; provider failures retain the queued delivery.
    }
}
