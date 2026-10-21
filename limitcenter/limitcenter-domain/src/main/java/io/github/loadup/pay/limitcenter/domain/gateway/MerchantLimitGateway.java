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
package io.github.loadup.pay.limitcenter.domain.gateway;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.limitcenter.domain.model.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Persistence or integration port; implementations own transactions and trusted tenant isolation. */
public interface MerchantLimitGateway {
    LimitOperationGate lockOrCreateGate(String tenantId, String operationId, String paymentId);

    Optional<LimitReservation> findByOperation(String tenantId, String operationId);

    LimitReservation lockReservation(String tenantId, String operationId);

    List<LimitReservationItem> lockItems(String tenantId, String reservationId);

    LimitBucketSet lockBuckets(String tenantId, List<LimitBucketKey> keys);

    void saveGate(String tenantId, LimitOperationGate gate);

    void saveReservation(
            String tenantId,
            LimitReservation reservation,
            List<LimitReservationItem> items,
            LimitBucketSet buckets,
            long expectedVersion);
}
