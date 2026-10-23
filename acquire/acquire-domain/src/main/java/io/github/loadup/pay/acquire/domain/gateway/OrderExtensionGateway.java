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
import io.github.loadup.pay.acquire.domain.model.OrderExtensionReceipt;

import java.util.Optional;

public interface OrderExtensionGateway {
    Optional<OrderExtensionReceipt> findReceipt(String tenant, String orderId, String requestKey);

    /**
     * Read under the already-held order lock and verify the header still matches the domain order.
     */
    long deadlineVersion(String tenant, AcquireOrder order);

    void extend(String tenant, AcquireOrder updated, OrderExtensionReceipt receipt);
}
