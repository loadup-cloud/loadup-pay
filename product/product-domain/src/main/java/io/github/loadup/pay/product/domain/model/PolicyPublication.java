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
package io.github.loadup.pay.product.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Frozen activation intent; only a matching authenticated activation ACK can make it ACTIVE.
 */
public record PolicyPublication(
        String id,
        String contractId,
        int contractRevision,
        String snapshotHash,
        String payeeMerchantId,
        String productScopeKey,
        String policyId,
        int policyVersion,
        String policyHash,
        @DiagnosticHidden String activationPayload,
        String state,
        String outboxEventId,
        LocalDateTime requestedAt,
        LocalDateTime activatedAt,
        int publicationSchemaVersion,
        long rowVersion) {
    public PolicyPublication {
        DomainChecks.id(id);
        DomainChecks.id(contractId);
        DomainChecks.text(snapshotHash, 128);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(productScopeKey, 128);
        DomainChecks.id(policyId);
        require(
                contractRevision == 1 && policyVersion == 1 && publicationSchemaVersion == 1 && rowVersion >= 0,
                INVALID_ARGUMENT);
        require(policyHash != null && policyHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        DomainChecks.text(activationPayload, 65536);
        Objects.requireNonNull(requestedAt);
        require(requestedAt.getNano() % 1000 == 0, INVALID_ARGUMENT);
        require(java.util.Set.of("INIT", "PUBLISHING", "ACTIVE").contains(state), INVALID_STATE);
        if ("INIT".equals(state)) require(outboxEventId == null && activatedAt == null, INVALID_STATE);
        else DomainChecks.id(outboxEventId);
        if ("ACTIVE".equals(state)) require(activatedAt != null && activatedAt.getNano() % 1000 == 0, INVALID_STATE);
        else require(activatedAt == null, INVALID_STATE);
    }

    public PolicyPublication publishing(String event) {
        require("INIT".equals(state), INVALID_STATE);
        DomainChecks.id(event);
        return copy("PUBLISHING", event, null);
    }

    public PolicyPublication acknowledge(String event, LocalDateTime at) {
        require(Objects.equals(outboxEventId, event) && at != null && at.getNano() % 1000 == 0, CONFLICT);
        if ("ACTIVE".equals(state)) {
            require(activatedAt.equals(at), CONFLICT);
            return this;
        }
        require("PUBLISHING".equals(state), INVALID_STATE);
        return copy("ACTIVE", event, at);
    }

    public void requireSource(String contract, int revision, String hash, String merchant, String scope) {
        require(
                contractId.equals(contract)
                        && contractRevision == revision
                        && snapshotHash.equals(hash)
                        && payeeMerchantId.equals(merchant)
                        && productScopeKey.equals(scope),
                CONFLICT);
    }

    public void requireSameIntent(PolicyPublication other) {
        requireSource(
                other.contractId,
                other.contractRevision,
                other.snapshotHash,
                other.payeeMerchantId,
                other.productScopeKey);
        require(
                id.equals(other.id)
                        && policyId.equals(other.policyId)
                        && policyVersion == other.policyVersion
                        && policyHash.equals(other.policyHash)
                        && activationPayload.equals(other.activationPayload)
                        && Objects.equals(outboxEventId, other.outboxEventId)
                        && requestedAt.equals(other.requestedAt),
                CONFLICT);
    }

    private PolicyPublication copy(String next, String event, LocalDateTime at) {
        return new PolicyPublication(
                id,
                contractId,
                contractRevision,
                snapshotHash,
                payeeMerchantId,
                productScopeKey,
                policyId,
                policyVersion,
                policyHash,
                activationPayload,
                next,
                event,
                requestedAt,
                at,
                1,
                Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
