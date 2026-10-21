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
package io.github.loadup.pay.payment.domain.gateway;

import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundRequest;

import java.util.List;
import java.util.Optional;

/** Persistence or integration port; implementations own transactions and trusted tenant isolation. */
public interface RefundGateway {
    Optional<RefundRequest> find(String tenantId, String refundRequestId);

    Optional<RefundRequest> findMerchantRefund(String tenantId, String payeeMerchantId, String merchantRefundNo);

    /** Lock parent gate, then request, capacity by paymentId, then child; never reverse that order. */
    RefundRequest lock(String tenantId, String refundRequestId);

    List<PaymentRefundCapacity> lockCapacities(String tenantId, String orderId);

    void savePlan(String tenantId, RefundRequest request, List<PaymentRefundCapacity> capacities);

    void saveOutcome(String tenantId, RefundRequest request, PaymentRefundCapacity capacity, long expectedVersion);
}
