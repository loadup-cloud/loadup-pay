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
package io.github.loadup.merchantcenter.domain.gateway;

import io.github.loadup.merchantcenter.domain.model.MerchantFacts;
import java.util.Optional;

/**
 * Persistence port; application owns the transaction and tenant/schema routing is resolved from the
 * execution context before the connection is acquired.
 */
public interface MerchantFactsGateway {
    /**
     * Serializes eligibility and authorization changes for this payee in the current transaction.
     */
    MerchantFacts lock(String merchantId);

    Optional<MerchantFacts> find(String merchantId);
}
