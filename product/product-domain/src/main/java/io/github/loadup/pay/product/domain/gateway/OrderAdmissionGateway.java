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
package io.github.loadup.pay.product.domain.gateway;

import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;

import java.util.Optional;

/**
 * Application owns transactions; duplicate inserts must roll back before reading the winner.
 */
public interface OrderAdmissionGateway {
    Optional<StoredOrderAdmission> findByOrder(String tenantId, String orderId);

    Optional<StoredOrderAdmission> findById(String tenantId, String admissionId);

    void insert(String tenantId, StoredOrderAdmission admission);
}
