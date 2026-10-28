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

import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderClosureGateway {
    Optional<OrderCloseIntent> findIntent(String tenant, String orderId);

    /**
     * Persist only the exact INIT to PENDING transition prescribed by an expired KEEP_PENDING order.
     */
    void keepPending(String tenant, AcquireOrder before, AcquireOrder after);

    void start(String tenant, AcquireOrder closing, OrderCloseIntent intent, long expectedOrderVersion);

    List<StoredPaymentAllocation> heldAfter(String tenant, String orderId, String afterPaymentId, int limit);

    void advanceCursor(String tenant, OrderCloseIntent updated, long expectedVersion);

    boolean allAllocationsSettled(String tenant, String orderId);

    void finish(String tenant, AcquireOrder closed, long expectedOrderVersion, LocalDateTime closedAt);
}
