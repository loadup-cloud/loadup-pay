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
 * One immutable file intent and one native task. Parser failures never become reconciliation success.
 */
public record ReconciliationImportRequest(String id, String batchId, String callerServiceId, String requestKey,
                                          String requestDigest, ReconciliationScope scope,
                                          ReconciliationStatementAccount statementAccount,
                                          ReconciliationFileSnapshot file, ReconciliationSourceKind sourceKind,
                                          String taskId,
                                          ReconciliationImportState state, String artifactId, String rejectionCode,
                                          long version) {
    public ReconciliationImportRequest {
        DomainChecks.id(id);
        DomainChecks.id(batchId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.id(taskId);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(statementAccount);
        Objects.requireNonNull(file);
        require(sourceKind != null && state != null && version >= 0, INVALID_ARGUMENT);
        require(version == (state == ReconciliationImportState.INIT ? 0L : 1L), INVALID_STATE);
        require((state == ReconciliationImportState.SEALED) == (artifactId != null), INVALID_STATE);
        require((state == ReconciliationImportState.REJECTED) == (rejectionCode != null), INVALID_STATE);
        if (artifactId != null) {
            require(id.equals(artifactId), CONFLICT);
        }
        if (rejectionCode != null) {
            DomainChecks.text(rejectionCode, 64);
        }
    }

    public static ReconciliationImportRequest init(String id, String batchId, String caller, String key, String digest,
                                                   ReconciliationScope scope, ReconciliationStatementAccount account, ReconciliationFileSnapshot file, String taskId) {
        return new ReconciliationImportRequest(id, batchId, caller, key, digest, scope, account, file,
                ReconciliationSourceKind.MANUAL_UPLOAD, taskId, ReconciliationImportState.INIT, null, null, 0L);
    }

    public ReconciliationImportRequest seal(ReconciliationManifest manifest) {
        require(manifest != null && manifest.side() == ReconciliationSide.CHANNEL && manifest.scope().equals(scope)
                && manifest.artifactId().equals(id) && manifest.sourceCaptureId().equals(id)
                && manifest.artifactSha256().equals(file.sha256()) && manifest.parserRevision().equals(scope.statementProfile()), CONFLICT);
        if (state == ReconciliationImportState.SEALED) {
            return this;
        }
        require(state == ReconciliationImportState.INIT, INVALID_STATE);
        return new ReconciliationImportRequest(id, batchId, callerServiceId, requestKey, requestDigest, scope, statementAccount, file,
                sourceKind, taskId, ReconciliationImportState.SEALED, id, null, Math.incrementExact(version));
    }

    public ReconciliationImportRequest reject(String code) {
        DomainChecks.text(code, 64);
        if (state == ReconciliationImportState.REJECTED) {
            require(code.equals(rejectionCode), CONFLICT);
            return this;
        }
        require(state == ReconciliationImportState.INIT, INVALID_STATE);
        return new ReconciliationImportRequest(id, batchId, callerServiceId, requestKey, requestDigest, scope, statementAccount, file,
                sourceKind, taskId, ReconciliationImportState.REJECTED, null, code, Math.incrementExact(version));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
