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
package io.github.loadup.pay.payment.infrastructure.outbox;

import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxHandler;
import io.github.loadup.pay.payment.app.service.PaymentEvents;
import io.github.loadup.pay.payment.app.service.PaymentLimitOutcomeDelivery;
import org.springframework.stereotype.Component;

@Component
public class PaymentLimitOutcomeOutboxHandler implements OutboxHandler {
    private final PaymentLimitOutcomeDelivery delivery;

    public PaymentLimitOutcomeOutboxHandler(PaymentLimitOutcomeDelivery delivery) {
        this.delivery = delivery;
    }

    @Override
    public String eventType() {
        return PaymentEvents.LIMIT;
    }

    @Override
    public void handle(OutboxEvent event) {
        delivery.deliver(event);
    }
}
