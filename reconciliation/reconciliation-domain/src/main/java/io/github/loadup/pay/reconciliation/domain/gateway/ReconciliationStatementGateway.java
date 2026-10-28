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
package io.github.loadup.pay.reconciliation.domain.gateway;

import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;

import java.util.Optional;

/**
 * Mutations and active lookup require the original batch lock first.
 */
public interface ReconciliationStatementGateway {
    Optional<ReconciliationStatementRequest> findRequest(String tenant, String caller, String requestKey);

    Optional<ReconciliationStatementRequest> find(String tenant, String id);

    Optional<ReconciliationStatementRequest> lock(String tenant, String id);

    Optional<ReconciliationStatementRequest> activeForBatch(String tenant, String batchId);

    void insert(String tenant, ReconciliationStatementRequest value);

    void update(String tenant, ReconciliationStatementRequest value, long expectedVersion);
}
