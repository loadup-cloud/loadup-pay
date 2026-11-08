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
package io.github.loadup.payment.app.service;

import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.contracts.model.PaymentState;
import io.github.loadup.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.payment.app.converter.PaymentOutcomeDeliveryConverter;
import io.github.loadup.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.payment.app.port.PaymentLimitClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentLimitOutcomeDelivery {
    private final PaymentOutcomeDeliverySupport support;
    private final PaymentLimitClient limits;
    private final PaymentAcceptanceCodec intents;
    private final PaymentOutcomeDeliveryConverter converter;

    public PaymentLimitOutcomeDelivery(
            PaymentOutcomeDeliverySupport support,
            PaymentLimitClient limits,
            PaymentAcceptanceCodec intents,
            PaymentOutcomeDeliveryConverter converter) {
        this.support = support;
        this.limits = limits;
        this.intents = intents;
        this.converter = converter;
    }

    public void deliver(OutboxEvent event) {
        var snapshot = support.load(event, PaymentEvents.LIMIT); // All local locks are released on return.
        var payment = snapshot.payment();
        var envelope = snapshot.envelope();
        if (payment.payment().state() == PaymentState.SUCCEEDED) {
            var ack = limits.confirm(converter.toConfirm(payment, envelope.businessEventId(), snapshot.succeededAt()));
            reservation(snapshot, ack);
            if (ack.state() != LimitReservationState.CONFIRMED) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
        } else {
            var ack = limits.release(converter.toRelease(payment, envelope.businessEventId()));
            if (ack == null
                    || !ack.fenced()
                    || !payment.payment().id().equals(ack.paymentId())
                    || !payment.payment().limitOperationId().equals(ack.operationId())
                    || payment.payment().version() != ack.paymentVersion()
                    || !payment.terminalEvidenceId().equals(ack.noCollectionEvidenceId())) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            if (ack.reservation() != null) {
                reservation(snapshot, ack.reservation());
                if (ack.reservation().state() != LimitReservationState.RELEASED
                        && ack.reservation().state() != LimitReservationState.EXPIRED) {
                    throw new CommonException(DEPENDENCY_UNAVAILABLE);
                }
            }
        }
    }

    private void reservation(PaymentDeliverySnapshot snapshot, LimitReservationDTO ack) {
        var source = snapshot.payment().payment();
        var policy = intents.decode(snapshot.payment()).admittedTerms().terms().limits();
        if (ack == null
                || ack.reservationId() == null
                || ack.reservationId().isBlank()
                || ack.state() == null
                || ack.version() < 1
                || !source.limitOperationId().equals(ack.operationId())
                || !source.id().equals(ack.paymentId())
                || !policy.policyId().equals(ack.policyId())
                || policy.policyVersion() != ack.policyVersion()
                || !BusinessTime.external(source.initiatedAt()).equals(ack.initiatedAt())) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
