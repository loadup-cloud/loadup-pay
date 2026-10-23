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

import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.RiskDecisionType;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;

final class OutcomeDeliveryFixtures {
    static PaymentDeliverySnapshot snapshot(boolean succeeded, boolean consulted, String type) {
        var prepared = AttemptFixtures.prepared();
        StoredPaymentAttempt attempt;
        var original = AttemptFixtures.accepted(true);
        PaymentOrder payment;
        if (succeeded) {
            var submitted = prepared.beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
            attempt = submitted.applyOutcome(new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.SUCCEEDED,
                    submitted.attempt().identity(), submitted.attempt().amount(), "evidence"), false);
            payment = original.payment().applyAttempt(attempt.attempt());
        } else {
            attempt = prepared.closeBeforeSubmission("evidence", consulted ? new RiskDecision(RiskDecisionType.DENY, "RULE_REJECTED", "consultation") : null);
            payment = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        }
        var stored = new StoredPaymentAcceptance(payment, original.admissionId(), original.requestDigest(), original.callerServiceId(),
                original.intentPayload(), original.payloadHash(), "evidence");
        var data = new PaymentOutcomeEventDTO(payment.id(), payment.orderId(), payment.allocationId(), payment.limitOperationId(),
                payment.payeeMerchantId(), payment.amount().getCent(), payment.amount().getCurrencyValue(),
                io.github.loadup.pay.payment.client.enums.PaymentState.valueOf(payment.state().name()), payment.version(), "evidence", !succeeded);
        var envelope = new EventEnvelope<>("delivery", "evidence", "payment", type, 1, payment.id(), payment.version(),
                BusinessTime.external(AttemptFixtures.NOW), data);
        return new PaymentDeliverySnapshot("tenant", envelope, stored, attempt, succeeded ? AttemptFixtures.NOW : null);
    }
}
