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

import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;

import java.util.Optional;

public interface PaymentAttemptGateway {
    Optional<StoredPaymentAttempt> find(String tenant, String attemptId);

    Optional<StoredPaymentAttempt> findRequest(String tenant, String paymentId, String requestKey);

    /**
     * Lookup is constrained by the trusted endpoint tenant and immutable binding revision.
     */
    Optional<StoredPaymentAttempt> findChannelRequest(String tenant, String bindingRevisionId, String channelRequestNo);

    Optional<StoredPaymentAttempt> lock(String tenant, String attemptId);

    void insert(String tenant, StoredPaymentAttempt value);

    void update(String tenant, StoredPaymentAttempt value, long expectedVersion);
}
