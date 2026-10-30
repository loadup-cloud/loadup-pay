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
 * Append-only input sealing and one durable run. Infrastructure failures never manufacture matched results.
 */
public record ReconciliationBatch(String id, ReconciliationScope scope, ReconciliationManifest channel,
                                  ReconciliationManifest ledger, ReconciliationState state, String runId,
                                  ReconciliationResultSummary result, long version) {
    public ReconciliationBatch {
        DomainChecks.id(id);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(state);
        require(version >= 0, INVALID_ARGUMENT);
        if (channel != null) {
            require(channel.side() == ReconciliationSide.CHANNEL && scope.equals(channel.scope()), CONFLICT);
        }
        if (ledger != null) {
            require(ledger.side() == ReconciliationSide.LEDGER && scope.equals(ledger.scope()), CONFLICT);
        }
        boolean both = channel != null && ledger != null;
        if (both) {
            require(!channel.artifactId().equals(ledger.artifactId())
                    && !channel.sourceCaptureId().equals(ledger.sourceCaptureId()), CONFLICT);
        }
        require((state == ReconciliationState.INIT) == !both, INVALID_STATE);
        boolean terminal = state == ReconciliationState.MATCHED || state == ReconciliationState.DIFFERENCES;
        require(terminal == (result != null), INVALID_STATE);
        require((state == ReconciliationState.RUNNING || terminal) == (runId != null), INVALID_STATE);
        if (runId != null) {
            DomainChecks.id(runId);
        }
        if (terminal) {
            require((state == ReconciliationState.MATCHED) == (result.differenceCount() == 0), CONFLICT);
        }
    }

    public static ReconciliationBatch init(String id, ReconciliationScope scope) {
        return new ReconciliationBatch(id, scope, null, null, ReconciliationState.INIT, null, null, 0L);
    }

    public ReconciliationBatch attach(ReconciliationManifest input) {
        require(state == ReconciliationState.INIT || state == ReconciliationState.READY, INVALID_STATE);
        require(input != null && scope.equals(input.scope()), CONFLICT);
        var prior = input.side() == ReconciliationSide.CHANNEL ? channel : ledger;
        if (prior != null) {
            require(prior.equals(input), CONFLICT);
            return this;
        }
        var nextChannel = input.side() == ReconciliationSide.CHANNEL ? input : channel;
        var nextLedger = input.side() == ReconciliationSide.LEDGER ? input : ledger;
        if (nextChannel != null && nextLedger != null) {
            require(!nextChannel.artifactId().equals(nextLedger.artifactId())
                    && !nextChannel.sourceCaptureId().equals(nextLedger.sourceCaptureId()), CONFLICT);
        }
        return new ReconciliationBatch(id, scope, nextChannel, nextLedger,
                nextChannel != null && nextLedger != null ? ReconciliationState.READY : ReconciliationState.INIT,
                null, null, Math.incrementExact(version));
    }

    public ReconciliationBatch begin(String executionId) {
        DomainChecks.id(executionId);
        if (state == ReconciliationState.RUNNING) {
            require(executionId.equals(runId), CONFLICT);
            return this;
        }
        require(state == ReconciliationState.READY, INVALID_STATE);
        return new ReconciliationBatch(id, scope, channel, ledger, ReconciliationState.RUNNING, executionId, null, Math.incrementExact(version));
    }

    public ReconciliationBatch complete(String executionId, ReconciliationResult comparison) {
        require(Objects.equals(executionId, runId) && runId != null && comparison != null
                && channel.equals(comparison.channel()) && ledger.equals(comparison.ledger()), CONFLICT);
        var summary = comparison.summary();
        if (state == ReconciliationState.MATCHED || state == ReconciliationState.DIFFERENCES) {
            require(result.equals(summary), CONFLICT);
            return this;
        }
        require(state == ReconciliationState.RUNNING, INVALID_STATE);
        return new ReconciliationBatch(id, scope, channel, ledger,
                summary.differenceCount() == 0 ? ReconciliationState.MATCHED : ReconciliationState.DIFFERENCES,
                runId, summary, Math.incrementExact(version));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
