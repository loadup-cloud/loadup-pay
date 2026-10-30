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
package io.github.loadup.pay.reconciliation.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Network intent is persisted first. READY means original files and Transfer are durable, not that parsing succeeded.
 */
public record ReconciliationStatementRequest(String id, String batchId, String callerServiceId, String requestKey,
                                             String requestDigest, ReconciliationScope scope,
                                             ReconciliationStatementAccount statementAccount,
                                             String bindingRevisionId, int maxBytes, String taskId,
                                             ReconciliationStatementState state,
                                             ReconciliationOfficialReceipt receipt, long version) {
    public ReconciliationStatementRequest {
        DomainChecks.id(id);
        DomainChecks.id(batchId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.id(bindingRevisionId);
        DomainChecks.id(taskId);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(statementAccount);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}") && maxBytes > 0 && maxBytes <= 16777216, INVALID_ARGUMENT);
        require(state != null && version == (state == ReconciliationStatementState.INIT ? 0L : 1L), INVALID_STATE);
        require((state == ReconciliationStatementState.READY) == (receipt != null), INVALID_STATE);
        if (receipt != null) {
            require(bindingRevisionId.equals(receipt.bindingRevisionId()) && receipt.content().size() <= maxBytes, CONFLICT);
        }
    }

    public static ReconciliationStatementRequest init(String id, String batchId, String caller, String key, String digest,
                                                      ReconciliationScope scope, ReconciliationStatementAccount account, String binding, int maxBytes, String taskId) {
        return new ReconciliationStatementRequest(id, batchId, caller, key, digest, scope, account, binding, maxBytes, taskId, ReconciliationStatementState.INIT, null, 0L);
    }

    public ReconciliationStatementRequest ready(ReconciliationOfficialReceipt value) {
        Objects.requireNonNull(value);
        if (state == ReconciliationStatementState.READY) {
            require(receipt.equals(value), CONFLICT);
            return this;
        }
        require(state == ReconciliationStatementState.INIT, INVALID_STATE);
        return new ReconciliationStatementRequest(id, batchId, callerServiceId, requestKey, requestDigest, scope, statementAccount,
                bindingRevisionId, maxBytes, taskId, ReconciliationStatementState.READY, value, 1L);
    }

    public ReconciliationStatementRequest supersede() {
        if (state == ReconciliationStatementState.SUPERSEDED) {
            return this;
        }
        require(state == ReconciliationStatementState.INIT, INVALID_STATE);
        return new ReconciliationStatementRequest(id, batchId, callerServiceId, requestKey, requestDigest, scope, statementAccount,
                bindingRevisionId, maxBytes, taskId, ReconciliationStatementState.SUPERSEDED, null, 1L);
    }

    public void verifyImport(ReconciliationImportRequest imported) {
        require(state == ReconciliationStatementState.READY && imported != null && id.equals(imported.id())
                && batchId.equals(imported.batchId()) && callerServiceId.equals(imported.callerServiceId())
                && ("official:" + id).equals(imported.requestKey()) && requestDigest.equals(imported.requestDigest())
                && scope.equals(imported.scope()) && statementAccount.equals(imported.statementAccount())
                && receipt.content().equals(imported.file()) && imported.sourceKind() == ReconciliationSourceKind.OFFICIAL_DOWNLOAD, CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
