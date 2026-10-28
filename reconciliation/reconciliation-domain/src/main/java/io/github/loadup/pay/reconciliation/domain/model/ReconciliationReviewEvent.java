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
 * Append-only command receipt/audit event; replay returns this exact event, never a later review head.
 */
public record ReconciliationReviewEvent(String id, ReconciliationReviewReference reference, String callerServiceId,
                                        String requestKey, String requestDigest,
                                        ReconciliationReviewOperation operation, String proposalId,
                                        ReconciliationReviewState state, long version, @DiagnosticHidden String note,
                                        LocalDateTime occurredAt) {
    public ReconciliationReviewEvent {
        DomainChecks.id(id);
        Objects.requireNonNull(reference);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.id(proposalId);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}") && version > 0, INVALID_ARGUMENT);
        Objects.requireNonNull(operation);
        Objects.requireNonNull(state);
        require(state == switch (operation) {
            case PROPOSE -> ReconciliationReviewState.PROPOSED;
            case APPROVE -> ReconciliationReviewState.APPROVED;
            case REJECT -> ReconciliationReviewState.REJECTED;
        }, CONFLICT);
        require(operation == ReconciliationReviewOperation.PROPOSE ? version % 2L == 1L : version >= 2L && version % 2L == 0L, INVALID_STATE);
        require(occurredAt != null && occurredAt.getNano() % 1000 == 0, INVALID_ARGUMENT);
        if (operation == ReconciliationReviewOperation.PROPOSE) require(note == null, INVALID_ARGUMENT);
        else
            require(note != null && !note.isBlank() && note.length() <= 1024 && note.chars().noneMatch(Character::isISOControl), INVALID_ARGUMENT);
    }

    public void verifyHead(ReconciliationReview value) {
        require(value != null && reference.equals(value.reference()) && id.equals(value.lastEventId())
                && proposalId.equals(value.proposalId()) && state == value.state() && version == value.version(), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
