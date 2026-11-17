/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.payment.app.dispatch;

import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.contracts.event.BusinessEventTypes;
import java.util.List;

/** Local subscription configuration; adding a destination does not change fact production. */
public record PaymentFactRoutes(List<Subscription> subscriptions) {
    public PaymentFactRoutes {
        subscriptions = List.copyOf(subscriptions);
        if (subscriptions.isEmpty()
                || subscriptions.stream()
                                .map(Subscription::deliveryType)
                                .distinct()
                                .count()
                        != subscriptions.size()) {
            throw new IllegalArgumentException("Payment delivery routes must be nonempty and unique");
        }
        for (String required : List.of(
                PaymentDeliveryRoutes.ACQUIRE,
                PaymentDeliveryRoutes.LIMIT,
                PaymentDeliveryRoutes.RISK,
                PaymentDeliveryRoutes.REALTIME,
                PaymentDeliveryRoutes.REFUND_CHILD,
                PaymentDeliveryRoutes.REFUND_SUMMARY,
                PaymentDeliveryRoutes.REFUND_FEE,
                PaymentDeliveryRoutes.REFUND_LIMIT,
                PaymentDeliveryRoutes.REFUND_MERCHANT)) {
            if (subscriptions.stream()
                    .noneMatch(route -> required.equals(route.deliveryType())
                            && PaymentDeliveryRoutes.factType(required).equals(route.factType()))) {
                throw new IllegalArgumentException("Missing or mismatched required payment delivery route");
            }
        }
    }

    public List<Subscription> forFact(String factType) {
        return subscriptions.stream()
                .filter(route -> route.factType().equals(factType))
                .toList();
    }

    public record Subscription(String factType, String deliveryType) {
        public Subscription {
            DomainChecks.text(factType, 128);
            DomainChecks.text(deliveryType, 128);
        }
    }

    public static PaymentFactRoutes defaults() {
        return new PaymentFactRoutes(List.of(
                new Subscription(BusinessEventTypes.PAYMENT_FINANCIAL, PaymentDeliveryRoutes.ACQUIRE),
                new Subscription(BusinessEventTypes.PAYMENT_FINANCIAL, PaymentDeliveryRoutes.LIMIT),
                new Subscription(BusinessEventTypes.PAYMENT_FINANCIAL, PaymentDeliveryRoutes.RISK),
                new Subscription(BusinessEventTypes.PAYMENT_SNAPSHOT, PaymentDeliveryRoutes.REALTIME),
                new Subscription(BusinessEventTypes.REFUND_SNAPSHOT, PaymentDeliveryRoutes.REFUND_CHILD),
                new Subscription(BusinessEventTypes.ORDER_REFUND_SUMMARY, PaymentDeliveryRoutes.REFUND_SUMMARY),
                new Subscription(BusinessEventTypes.ORDER_REFUND_SUMMARY, PaymentDeliveryRoutes.REFUND_FEE),
                new Subscription(BusinessEventTypes.REFUND_FINANCIAL, PaymentDeliveryRoutes.REFUND_LIMIT),
                new Subscription(BusinessEventTypes.REFUND_PROGRESS, PaymentDeliveryRoutes.REFUND_MERCHANT)));
    }
}
