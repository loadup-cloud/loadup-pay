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
package io.github.loadup.pay.acquire.domain.gateway;

import io.github.loadup.pay.acquire.domain.model.*;
import java.util.Optional;

public interface PaymentOutcomeGateway {
    Optional<StoredPaymentAllocation> findPayment(String tenant, String orderId, String paymentId);
    Optional<StoredPaymentAllocation> lockPayment(String tenant, String orderId, String paymentId);
    Optional<PaymentOutcomeReceipt> findReceipt(String tenant, String allocationId);
    void apply(String tenant, OrderPaymentChange change, StoredPaymentAllocation original,
               PaymentOutcomeReceipt receipt, long expectedOrderVersion);
}
