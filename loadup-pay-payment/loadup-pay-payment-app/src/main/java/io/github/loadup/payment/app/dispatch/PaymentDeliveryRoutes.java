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

import io.github.loadup.contracts.event.BusinessEventTypes;

/** Persisted target queue routes, deliberately independent of EventEnvelope.eventType. */
public final class PaymentDeliveryRoutes {
    public static final String ACQUIRE = "pay.payment.outcome.acquire.v1";
    public static final String LIMIT = "pay.payment.outcome.limit.v1";
    public static final String REALTIME = "pay.payment.snapshot.realtime.v1";
    public static final String RISK = "pay.payment.outcome.risk.v1";
    public static final String REFUND_CHILD = "pay.payment.refund.realtime.v1";
    public static final String REFUND_SUMMARY = "pay.payment.refund-summary.realtime.v1";
    public static final String REFUND_FEE = "pay.payment.refund.fee.v1";
    public static final String REFUND_LIMIT = "pay.payment.refund.limit.v1";
    public static final String REFUND_MERCHANT = "pay.payment.refund.merchant.v1";

    private PaymentDeliveryRoutes() {}

    public static String factType(String route) {
        return switch (route) {
            case ACQUIRE, LIMIT, RISK -> BusinessEventTypes.PAYMENT_FINANCIAL;
            case REALTIME -> BusinessEventTypes.PAYMENT_SNAPSHOT;
            case REFUND_CHILD -> BusinessEventTypes.REFUND_SNAPSHOT;
            case REFUND_SUMMARY, REFUND_FEE -> BusinessEventTypes.ORDER_REFUND_SUMMARY;
            case REFUND_LIMIT -> BusinessEventTypes.REFUND_FINANCIAL;
            case REFUND_MERCHANT -> BusinessEventTypes.REFUND_PROGRESS;
            default -> throw new IllegalArgumentException("Unknown payment delivery route");
        };
    }
}
