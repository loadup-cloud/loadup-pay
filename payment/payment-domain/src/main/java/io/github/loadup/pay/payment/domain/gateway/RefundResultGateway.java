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

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.domain.model.RefundChannelReceipt;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.payment.domain.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundResultAnomaly;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;

import java.util.Optional;

public interface RefundResultGateway {
    Optional<String> requestId(String tenant, String refundId);

    /**
     * Caller already holds parent -> request -> capacities; locks only this child.
     */
    RefundExecutionFacts lockChild(String tenant, String refundId);

    Optional<RefundChannelReceipt> receipt(String tenant, String refundId, RefundObservation.Source source, String key);

    void insertReceipt(String tenant, RefundChannelReceipt receipt);

    void recordAnomaly(String tenant, RefundResultAnomaly anomaly);

    Optional<RefundTransactionClaim> transaction(Channel channel, String scope, String transactionNo);

    Optional<RefundTransactionClaim> claim(String tenant, String refundId);

    void insertClaim(RefundTransactionClaim claim);

    /**
     * Child, request and changed capacity CAS share the caller's transaction. Never changes original collection rows.
     */
    void update(String tenant, RefundProgressChange change);
}
