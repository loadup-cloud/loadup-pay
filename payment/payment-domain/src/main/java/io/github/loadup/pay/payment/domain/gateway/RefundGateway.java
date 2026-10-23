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
import io.github.loadup.pay.payment.domain.model.RefundCapacityReservation;
import io.github.loadup.pay.payment.domain.model.RefundRequestReference;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;

import java.util.List;
import java.util.Optional;

/**
 * The caller owns the parent gate lock and one writable payment transaction.
 */
public interface RefundGateway {
    Optional<RefundRequestReference> reference(String tenant, String id);

    Optional<RefundRequestReference> merchantReference(String tenant, String merchant, String merchantRefundNo);

    StoredRefundRequest load(String tenant, String id);

    StoredRefundRequest lock(String tenant, String id);

    /**
     * Ascending paymentId locks; FIFO sorting happens only after every capacity is locked.
     */
    List<PaymentRefundCapacity> lockCapacities(String tenant, String orderId);

    /**
     * Request, immutable plan, stable children and all capacity CAS writes are one local transaction.
     */
    void insertPlan(String tenant, StoredRefundRequest value, List<RefundCapacityReservation> reservations);
}
