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

/** Local storage only; the application owns the A0/A1 transaction boundaries. */
public interface OrderCreationGateway {
    Optional<OrderCreateIntent> findIntent(String tenant, String payee, String merchantOrderNo);

    OrderCreateIntent lockIntent(String tenant, String id);

    void insertIntent(String tenant, OrderCreateIntent intent);

    void updateIntent(String tenant, OrderCreateIntent intent, long expectedVersion);

    Optional<StoredAcquireOrder> findOrder(String tenant, String orderId);

    StoredAcquireOrder lockOrder(String tenant, String orderId);

    void insertOrder(String tenant, StoredAcquireOrder order, OrderFee fee, String feePolicyPayload);
}
