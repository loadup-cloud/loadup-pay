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
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Immutable maker proposal, including the exact optional private evidence snapshot.
 */
public record ReconciliationReviewProposal(String id, ReconciliationReviewReference reference, String proposerServiceId,
                                           ReconciliationReviewConclusion conclusion, @DiagnosticHidden String note,
                                           ReconciliationFileSnapshot evidence, LocalDateTime proposedAt) {
    public ReconciliationReviewProposal {
        DomainChecks.id(id);
        Objects.requireNonNull(reference);
        DomainChecks.id(proposerServiceId);
        Objects.requireNonNull(conclusion);
        require(note != null && !note.isBlank() && note.length() <= 1024 && note.chars().noneMatch(Character::isISOControl), INVALID_ARGUMENT);
        require(proposedAt != null && proposedAt.getNano() % 1000 == 0, INVALID_ARGUMENT);
        require(conclusion != ReconciliationReviewConclusion.INPUT_DEFECT || evidence != null, INVALID_ARGUMENT);
    }

    public void requireIndependentChecker(String service) {
        DomainChecks.id(service);
        require(!proposerServiceId.equals(service), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
