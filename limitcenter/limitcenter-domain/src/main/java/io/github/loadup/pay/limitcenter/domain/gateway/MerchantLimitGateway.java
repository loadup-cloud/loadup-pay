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

import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;

import java.util.List;
import java.util.Optional;

/**
 * Mutations join one caller transaction; all bucket changes and the operation transition commit together.
 */
public interface MerchantLimitGateway {
    LimitOperationGate lockOrCreateGate(String tenant, String operationId, String paymentId);

    Optional<LimitOperationGate> lockGate(String tenant, String operationId);

    Optional<LimitExecutionGrant> findExecutionGrant(String tenant, String operationId, long epoch);

    void insertExecutionGrant(String tenant, LimitExecutionGrant grant);

    List<LimitReservationItem> loadItems(String tenant, String reservationId);

    LimitBucketSet lockExistingBuckets(String tenant, List<LimitBucketKey> keys);

    void updateBuckets(String tenant, LimitBucketSet before, LimitBucketSet after);

    void updateReservation(String tenant, StoredLimitReservation stored, long expectedVersion);

    Optional<StoredLimitReservation> findByOperation(String tenant, String operationId);

    LimitBucketSet lockBuckets(String tenant, List<LimitBucketKey> keys);

    void saveGate(String tenant, LimitOperationGate gate);

    void insertReservation(
            String tenant,
            StoredLimitReservation stored,
            List<LimitReservationItem> items,
            LimitBucketSet before,
            LimitBucketSet after);
}
