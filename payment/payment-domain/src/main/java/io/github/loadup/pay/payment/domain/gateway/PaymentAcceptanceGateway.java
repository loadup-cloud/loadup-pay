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

import io.github.loadup.pay.payment.domain.model.*;
import java.util.Optional;

/** All writes and gate locks share the payment database transaction. */
public interface PaymentAcceptanceGateway {
    Optional<StoredPaymentAcceptance> find(String tenant, String paymentId);
    Optional<StoredPaymentAcceptance> findAllocation(String tenant, String allocationId);
    Optional<StoredPaymentGate> lockParent(String tenant, String orderId);
    Optional<StoredPaymentGate> lockPayment(String tenant, String paymentId);
    void insertParent(String tenant, StoredPaymentGate gate);
    void insertPaymentGate(String tenant, StoredPaymentGate gate);
    void insert(String tenant, StoredPaymentAcceptance acceptance);
}
