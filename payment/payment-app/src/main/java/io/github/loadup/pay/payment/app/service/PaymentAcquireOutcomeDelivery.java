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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import org.springframework.stereotype.Component;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;

@Component
public class PaymentAcquireOutcomeDelivery {
    private final PaymentOutcomeDeliverySupport support;
    private final PaymentAcquireClient acquire;

    public PaymentAcquireOutcomeDelivery(PaymentOutcomeDeliverySupport support, PaymentAcquireClient acquire) {
        this.support = support;
        this.acquire = acquire;
    }

    public void deliver(OutboxEvent event) {
        var snapshot = support.load(event, PaymentEvents.ACQUIRE);
        if (!acquire.supportsTenant(snapshot.tenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var envelope = snapshot.envelope();
        var ack = acquire.accept(snapshot.tenantId(), envelope);
        if (ack == null || !ack.accepted() || !envelope.deliveryId().equals(ack.deliveryId())
                || !envelope.businessEventId().equals(ack.businessEventId()) || !envelope.aggregateId().equals(ack.paymentId())
                || envelope.aggregateVersion() != ack.paymentVersion() || !envelope.data().evidenceId().equals(ack.evidenceId())
                || ack.orderVersion() < 0) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
