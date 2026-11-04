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

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.components.payment.channel.api.model.PaymentObservation;
import io.github.loadup.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.payment.domain.model.PaymentChannelReceipt;
import io.github.loadup.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.payment.domain.model.PaymentResultAnomaly;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentResultGateway {
    Optional<PaymentChannelReceipt> receipt(String attemptId, PaymentObservation.Source source, String key);

    void insertReceipt(PaymentChannelReceipt receipt);

    /**
     * Idempotent only for the same receipt/facts/reason, while holding the owning payment lock.
     */
    void recordAnomaly(PaymentResultAnomaly anomaly);

    /**
     * Exact global immutable financial key. Internal only; never expose another tenant's claim.
     */
    Optional<ChannelTransactionClaim> transaction(Channel channel, String accountScopeHash, String transactionNo);

    Optional<ChannelTransactionClaim> attemptClaim(String attemptId);

    void insertClaim(ChannelTransactionClaim claim);

    /**
     * State, evidence and authoritative channel success time are one payment-row CAS.
     */
    void completeSuccess(StoredPaymentAcceptance payment, long expectedVersion, LocalDateTime succeededAt);

    Optional<PaymentRefundCapacity> capacity(String paymentId);

    void insertCapacity(String id, PaymentRefundCapacity capacity);
}
