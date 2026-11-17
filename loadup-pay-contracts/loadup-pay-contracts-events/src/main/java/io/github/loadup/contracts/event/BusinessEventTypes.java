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
package io.github.loadup.contracts.event;

/** Business vocabulary only; delivery destinations are not part of a fact type. */
public final class BusinessEventTypes {
    public static final String PAYMENT_OUTCOME = "pay.payment.outcome.v1";
    public static final String PAYMENT_SNAPSHOT = "pay.payment.snapshot.v1";
    public static final String REFUND_OUTCOME = "pay.payment.refund-outcome.v1";
    public static final String REFUND_SNAPSHOT = "pay.payment.refund-snapshot.v1";
    public static final String ORDER_REFUND_SUMMARY = "pay.payment.order-refund-summary.v1";
    public static final String REFUND_PROGRESS = "pay.payment.refund-progress.v1";
    public static final String ORDER_SNAPSHOT = "pay.acquire.order-snapshot.v1";

    public static final String PAYMENT_FINANCIAL = "pay.payment.financial-outcome.v1";
    public static final String REFUND_FINANCIAL = "pay.payment.refund-financial-outcome.v1";

    private BusinessEventTypes() {}
}
