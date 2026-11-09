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
package io.github.loadup.payment.domain.gateway;

import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.model.StoredPaymentGate;
import java.util.Optional;

/**
 * All writes and gate locks share the payment database transaction.
 */
public interface PaymentAcceptanceGateway {
    Optional<StoredPaymentAcceptance> find(String paymentId);

    Optional<StoredPaymentAcceptance> findAllocation(String allocationId);

    Optional<StoredPaymentGate> lockParent(String orderId);

    Optional<StoredPaymentGate> lockPayment(String paymentId);

    void insertParent(StoredPaymentGate gate);

    void insertPaymentGate(StoredPaymentGate gate);

    void insert(StoredPaymentAcceptance acceptance);
}
