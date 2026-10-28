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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationDisposition;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationOutcome;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationVerificationRequestTest {
    final ReconciliationKey key = new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null);
    final ReconciliationReviewReference reference = new ReconciliationReviewReference("difference", "batch", "run", "a".repeat(64), key.fingerprint());
    final ReconciliationVerificationRequest initial = new ReconciliationVerificationRequest("request", reference, "proposal", "approval", 2L, "ops", "key", "b".repeat(64),
            new ReconciliationScope(Channel.WECHAT, "c".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116"), key, "owner-request", "delivery", ReconciliationVerificationState.INIT, null, 0L);

    @Test
    void coverageGapIsARecordedSnapshotAndNeverNoCollectionEvidence() {
        var gap = new ReconciliationVerificationReceipt(ReconciliationVerificationDisposition.NO_LOCAL_TARGET, null, null, null, null, null, null, null, null, null, null, null, null, 1000000L, 2000000L, null);
        var result = initial.complete(gap);
        assertEquals(ReconciliationVerificationState.RECORDED, result.state());
        assertNull(result.receipt().outcome());
        assertNull(result.receipt().amount());
        assertSame(result, result.complete(gap));
        var changed = new ReconciliationVerificationReceipt(ReconciliationVerificationDisposition.NOT_SUBMITTED, null, null, null, null, null, null, null, null, null, null, null, null, 1000000L, 2000000L, null);
        assertThrows(DomainViolation.class, () -> result.complete(changed));
        assertEquals(reference, result.reference());
    }

    @Test
    void incompatibleKindAndFakeGapAmountsCannotBecomeAReceipt() {
        assertThrows(DomainViolation.class, () -> new ReconciliationVerificationReceipt(ReconciliationVerificationDisposition.NO_LOCAL_TARGET, null, null, null, null, null, null, null, Money.ofMinor(1L, "CNY"), null, null, null, null, 1000000L, 2000000L, null));
        var refund = new ReconciliationVerificationReceipt(ReconciliationVerificationDisposition.OBSERVED, "revision", "payment", "attempt", "refund", 1L, "PROCESSING", ReconciliationVerificationOutcome.UNKNOWN, Money.ofMinor(30L, "CNY"), "transaction", null, null, "d".repeat(64), 1000000L, 2000000L, 1000000L);
        assertThrows(DomainViolation.class, () -> initial.complete(refund));
    }
}
