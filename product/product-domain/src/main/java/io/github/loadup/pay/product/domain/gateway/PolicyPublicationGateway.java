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

import io.github.loadup.pay.product.domain.model.PolicyPublication;

import java.util.Optional;

public interface PolicyPublicationGateway {
    Optional<PolicyPublication> findById(String tenant, String id);

    Optional<PolicyPublication> lockById(String tenant, String id);

    Optional<PolicyPublication> findByContract(String tenant, String contract, int revision);

    Optional<PolicyPublication> findByMerchant(String tenant, String merchant);

    Optional<PolicyPublication> findByPolicy(String tenant, String policy);

    void insert(String tenant, PolicyPublication value);

    void update(String tenant, PolicyPublication value, long expectedVersion);
}
