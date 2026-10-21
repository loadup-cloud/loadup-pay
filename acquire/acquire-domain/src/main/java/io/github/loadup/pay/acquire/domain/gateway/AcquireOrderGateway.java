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
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Persistence or integration port; implementations own transactions and trusted tenant isolation. */
public interface AcquireOrderGateway {
    Optional<AcquireOrder> find(String tenantId, String orderId);

    AcquireOrder lock(String tenantId, String orderId);

    Optional<OrderPaymentAllocation> findAction(String tenantId, String orderId, String actorScope, String actionKey);

    OrderPaymentAllocation lockAllocation(String tenantId, String paymentId);

    void save(String tenantId, AcquireOrder order, long expectedVersion);

    void saveChange(String tenantId, OrderPaymentChange change, long expectedOrderVersion);
}
