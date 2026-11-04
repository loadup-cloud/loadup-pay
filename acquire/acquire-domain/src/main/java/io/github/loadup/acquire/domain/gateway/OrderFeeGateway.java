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
package io.github.loadup.acquire.domain.gateway;

import io.github.loadup.acquire.domain.model.AcquireOrder;
import io.github.loadup.acquire.domain.model.OrderFeePolicy;
import java.time.LocalDateTime;

public interface OrderFeeGateway {
    /**
     * Joins the original order transaction; amount and policy remain frozen.
     */
    void confirmPaid(AcquireOrder paid, OrderFeePolicy expectedPolicy, LocalDateTime confirmedAt);

    /**
     * Authoritative cumulative refund facts only; caller holds the original order lock and writable transaction.
     */
    void applyRefundBasis(
            AcquireOrder order,
            OrderFeePolicy expectedPolicy,
            long sourceVersion,
            long cumulativeMinor,
            String sourceEventId,
            LocalDateTime appliedAt);
}
