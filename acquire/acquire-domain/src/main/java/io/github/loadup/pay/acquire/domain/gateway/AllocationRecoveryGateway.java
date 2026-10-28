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

import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryCursor;

import java.util.Optional;

/**
 * Joins the original parent/payment transaction; no independent execution claim.
 */
public interface AllocationRecoveryGateway {
    Optional<AllocationRecoveryCursor> find(String tenant, String paymentId);

    Optional<AllocationRecoveryCursor> lock(String tenant, String paymentId);

    void insert(String tenant, AllocationRecoveryCursor value);

    void update(String tenant, AllocationRecoveryCursor before, AllocationRecoveryCursor after);
}
