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
package io.github.loadup.pay.reconciliation.infrastructure.converter;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewConclusion;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewOperation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationReviewStorageConverterTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(ReconciliationReviewStorageConverter.class.getPackageName());
    final ReconciliationReviewStorageConverter converter = spring.getBean(ReconciliationReviewStorageConverter.class);
    final ReconciliationReviewReference reference = new ReconciliationReviewReference("difference", "batch", "run", "a".repeat(64), "b".repeat(64));
    final LocalDateTime at = LocalDateTime.of(2026, 10, 28, 14, 0, 0, 123456000);

    @AfterEach
    void close() {
        spring.close();
    }

    @Test
    void reviewHeadAndAppendOnlyReceiptRoundtripOriginalRunAndExactVersion() {
        var proposal = proposal(null);
        var head = ReconciliationReview.init(reference).propose(proposal, "event", 0L);
        var event = new ReconciliationReviewEvent("event", reference, "maker", "request", "c".repeat(64), ReconciliationReviewOperation.PROPOSE, "proposal", ReconciliationReviewState.PROPOSED, 1L, null, at);
        assertEquals(head, converter.head(converter.toDO("tenant", head)));
        assertEquals(event, converter.event(converter.toDO("tenant", event)));
        var approved = head.decide(proposal, "checker", "approved", true, 1L);
        assertEquals(approved, converter.head(converter.toDO("tenant", approved)));
    }

    @Test
    void absentEvidenceNeverBecomesImplicitZeroSizeAndPartialPresenceIsRejected() {
        var row = converter.toDO("tenant", proposal(null));
        assertNull(row.getEvidenceSize());
        assertNull(row.getEvidenceFileId());
        assertEquals(proposal(null), converter.proposal(row));
        row.setEvidenceSha256("d".repeat(64));
        assertThrows(CommonException.class, () -> converter.proposal(row));
    }

    @Test
    void completePrivateEvidenceAndBusinessTimestampKeepExactValues() {
        var file = new ReconciliationFileSnapshot("file", "private-storage", 321L, "d".repeat(64));
        var proposal = proposal(file);
        var row = converter.toDO("tenant", proposal);
        assertEquals(proposal, converter.proposal(row));
        assertEquals(at, row.getProposedAt());
        assertEquals(Long.valueOf(321L), row.getEvidenceSize());
        assertFalse(row.toString().contains("private rationale"));
        assertFalse(row.toString().contains("private-storage"));
    }

    ReconciliationReviewProposal proposal(ReconciliationFileSnapshot file) {
        return new ReconciliationReviewProposal("proposal", reference, "maker", ReconciliationReviewConclusion.TIMING_DIFFERENCE, "private rationale", file, at);
    }
}
