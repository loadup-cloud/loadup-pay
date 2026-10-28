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
package io.github.loadup.pay.reconciliation.domain;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewConclusion;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewOperation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationReviewTest {
    final ReconciliationReviewReference reference = new ReconciliationReviewReference("difference", "batch", "run", "a".repeat(64), "b".repeat(64));
    final LocalDateTime at = LocalDateTime.of(2026, 10, 28, 14, 0);
    final ReconciliationReviewProposal proposal = new ReconciliationReviewProposal("proposal", reference, "maker", ReconciliationReviewConclusion.CONFIRMED_DIFFERENCE, "private rationale", null, at);

    @Test
    void independentApprovalIsFinalAndCannotRewriteOriginalReference() {
        var initial = ReconciliationReview.init(reference);
        assertEquals(ReconciliationReviewState.INIT, initial.state());
        assertEquals(0L, initial.version());
        var pending = initial.propose(proposal, "event-1", 0L);
        assertThrows(DomainViolation.class, () -> pending.decide(proposal, "maker", "self", true, 1L));
        var approved = pending.decide(proposal, "checker", "event-2", true, 1L);
        assertEquals(reference, approved.reference());
        assertEquals(ReconciliationReviewState.APPROVED, approved.state());
        assertEquals(2L, approved.version());
        assertThrows(DomainViolation.class, () -> approved.propose(proposal, "event-3", 2L));
        assertThrows(DomainViolation.class, () -> approved.decide(proposal, "checker", "event-4", false, 2L));
    }

    @Test
    void rejectedProposalRetainsItsIdentityWhileNewProposalRequiresCurrentVersion() {
        var first = ReconciliationReview.init(reference).propose(proposal, "event-1", 0L);
        var rejected = first.decide(proposal, "checker", "event-2", false, 1L);
        var second = new ReconciliationReviewProposal("proposal-2", reference, "maker-2", ReconciliationReviewConclusion.TIMING_DIFFERENCE, "new private rationale", null, at.plusMinutes(1));
        assertThrows(DomainViolation.class, () -> rejected.propose(second, "event-3", 1L));
        var pending = rejected.propose(second, "event-3", 2L);
        assertEquals(3L, pending.version());
        assertEquals("proposal-2", pending.proposalId());
        assertThrows(DomainViolation.class, () -> pending.decide(proposal, "checker", "event-4", true, 3L));
        assertThrows(DomainViolation.class, () -> pending.decide(second, "maker-2", "event-4", true, 3L));
        assertEquals(ReconciliationReviewState.APPROVED, pending.decide(second, "checker", "event-4", true, 3L).state());
    }

    @Test
    void inputDefectRequiresPrivateEvidenceAndMalformedStateOrEventFailsClosed() {
        assertThrows(DomainViolation.class, () -> new ReconciliationReviewProposal("proposal", reference, "maker", ReconciliationReviewConclusion.INPUT_DEFECT, "private rationale", null, at));
        var file = new ReconciliationFileSnapshot("evidence", "private-storage", 100, "c".repeat(64));
        assertDoesNotThrow(() -> new ReconciliationReviewProposal("proposal", reference, "maker", ReconciliationReviewConclusion.INPUT_DEFECT, "private rationale", file, at));
        assertThrows(DomainViolation.class, () -> new ReconciliationReview(reference, ReconciliationReviewState.APPROVED, "proposal", "event", 1L));
        assertThrows(DomainViolation.class, () -> new ReconciliationReviewEvent("event", reference, "checker", "request", "d".repeat(64), ReconciliationReviewOperation.APPROVE, "proposal", ReconciliationReviewState.REJECTED, 2L, "private reason", at));
        assertFalse(proposal.toString().contains("private rationale"));
    }
}
