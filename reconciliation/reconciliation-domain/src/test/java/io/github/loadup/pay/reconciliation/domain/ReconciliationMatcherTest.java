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
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifferenceReason;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationEntryStatus;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationMatcher;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconciliationMatcherTest {
    private static final ReconciliationScope SCOPE = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "wechat-trade-v1");
    private final ReconciliationMatcher matcher = new ReconciliationMatcher();

    private static ReconciliationLine line(ReconciliationRecordKind kind, String transaction, String refund, String request, long amount,
                                           ReconciliationEntryStatus state, long ordinal) {
        return new ReconciliationLine(new ReconciliationKey(kind, transaction, refund), request, Money.ofMinor(amount, "CNY"), state, ordinal);
    }

    private static ReconciliationDataset dataset(ReconciliationSide side, ReconciliationScope scope, List<ReconciliationLine> lines) {
        var manifest = new ReconciliationManifest(side, scope, side.name() + "-artifact", (side == ReconciliationSide.CHANNEL ? "c" : "d").repeat(64),
                ReconciliationDataset.normalizedHash(scope, side, "parser-v1", lines), side.name() + "-capture", "parser-v1", ReconciliationTotals.count(lines));
        return new ReconciliationDataset(manifest, lines);
    }

    @Test
    void comparesGrossPaymentAndMultiplePartialRefundsIndependentlyAndDeterministically() {
        var pay = line(ReconciliationRecordKind.PAYMENT, "transaction", null, "original-pay", 1000, ReconciliationEntryStatus.SUCCEEDED, 1);
        var refund1 = line(ReconciliationRecordKind.REFUND, "transaction", "refund-1", "original-pay", 200, ReconciliationEntryStatus.SUCCEEDED, 2);
        var refund2 = line(ReconciliationRecordKind.REFUND, "transaction", "refund-2", "original-pay", 300, ReconciliationEntryStatus.PROCESSING, 3);
        var channel = dataset(ReconciliationSide.CHANNEL, SCOPE, List.of(refund2, pay, refund1));
        var ledger = dataset(ReconciliationSide.LEDGER, SCOPE, List.of(refund1, refund2, pay));
        var result = matcher.compare(channel, ledger);
        assertEquals(3L, result.matchedCount());
        assertTrue(result.differences().isEmpty());
        assertEquals(new ReconciliationTotals(1L, 1000L, 2L, 500L), result.channel().totals());
        assertEquals(result.summary(), matcher.compare(channel, ledger).summary());
        var tampered = line(ReconciliationRecordKind.PAYMENT, "different-transaction", null, "original-pay", 1000,
                ReconciliationEntryStatus.SUCCEEDED, 1);
        assertThrows(DomainViolation.class, () -> new ReconciliationDataset(channel.manifest(), List.of(tampered, refund1, refund2)));
        assertThrows(UnsupportedOperationException.class, () -> channel.records().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.differences().clear());
    }

    @Test
    void keepsEveryMismatchReasonAndDoesNotTreatPendingRefundsAsCompletedReturns() {
        var fromChannel = line(ReconciliationRecordKind.REFUND, "transaction", "refund-1", "original-pay", 200, ReconciliationEntryStatus.PROCESSING, 1);
        var fromLedger = line(ReconciliationRecordKind.REFUND, "transaction", "refund-1", "wrong-pay", 250, ReconciliationEntryStatus.SUCCEEDED, 9);
        var result = matcher.compare(dataset(ReconciliationSide.CHANNEL, SCOPE, List.of(fromChannel)),
                dataset(ReconciliationSide.LEDGER, SCOPE, List.of(fromLedger)));
        assertEquals(0, result.matchedCount());
        var difference = result.differences().getFirst();
        assertEquals(Set.of(ReconciliationDifferenceReason.REQUEST_MISMATCH, ReconciliationDifferenceReason.AMOUNT_MISMATCH,
                ReconciliationDifferenceReason.STATE_MISMATCH), difference.reasons());
        assertSame(fromChannel, difference.channel());
        assertSame(fromLedger, difference.ledger());
    }

    @Test
    void completeInputsDistinguishBothOrphanDirectionsAndWrongAccountDateOrProfileCannotMatch() {
        var payment = line(ReconciliationRecordKind.PAYMENT, "channel-only", null, "request-1", 100, ReconciliationEntryStatus.SUCCEEDED, 1);
        var refund = line(ReconciliationRecordKind.REFUND, "ledger-only", "refund", "request-2", 200, ReconciliationEntryStatus.UNKNOWN, 1);
        var channel = dataset(ReconciliationSide.CHANNEL, SCOPE, List.of(payment));
        var ledger = dataset(ReconciliationSide.LEDGER, SCOPE, List.of(refund));
        var result = matcher.compare(channel, ledger);
        assertEquals(Set.of(ReconciliationDifferenceReason.CHANNEL_ONLY), result.differences().getFirst().reasons());
        assertEquals(Set.of(ReconciliationDifferenceReason.LEDGER_ONLY), result.differences().getLast().reasons());
        for (var scope : List.of(new ReconciliationScope(Channel.WECHAT, "b".repeat(64), SCOPE.billDate(), SCOPE.statementProfile()),
                new ReconciliationScope(Channel.WECHAT, SCOPE.accountScopeHash(), SCOPE.billDate().plusDays(1), SCOPE.statementProfile()),
                new ReconciliationScope(Channel.WECHAT, SCOPE.accountScopeHash(), SCOPE.billDate(), "another-profile"))) {
            assertThrows(DomainViolation.class, () -> matcher.compare(channel, dataset(ReconciliationSide.LEDGER, scope, List.of(payment))));
        }
    }

    @Test
    void duplicateKeysDuplicateSourceOrdinalsAndBadTrailersInvalidateTheWholeInput() {
        var payment = line(ReconciliationRecordKind.PAYMENT, "transaction", null, "request", 100, ReconciliationEntryStatus.SUCCEEDED, 1);
        var repeatedKey = line(ReconciliationRecordKind.PAYMENT, "transaction", null, "request", 100, ReconciliationEntryStatus.SUCCEEDED, 2);
        var repeatedOrdinal = line(ReconciliationRecordKind.PAYMENT, "different", null, "request", 100, ReconciliationEntryStatus.SUCCEEDED, 1);
        assertThrows(DomainViolation.class, () -> dataset(ReconciliationSide.CHANNEL, SCOPE, List.of(payment, repeatedKey)));
        assertThrows(DomainViolation.class, () -> dataset(ReconciliationSide.CHANNEL, SCOPE, List.of(payment, repeatedOrdinal)));
        var manifest = new ReconciliationManifest(ReconciliationSide.CHANNEL, SCOPE, "artifact", "a".repeat(64), "b".repeat(64), "capture", "parser-v1",
                new ReconciliationTotals(1L, 101L, 0L, 0L));
        assertThrows(DomainViolation.class, () -> new ReconciliationDataset(manifest, List.of(payment)));
        var nearOverflow = line(ReconciliationRecordKind.PAYMENT, "large", null, "request", Long.MAX_VALUE, ReconciliationEntryStatus.SUCCEEDED, 2);
        assertThrows(DomainViolation.class, () -> ReconciliationTotals.count(List.of(payment, nearOverflow)));
    }

    @Test
    void onlySealedOriginalInputsAndOriginalRunCanCompleteTheBatchAndCompletionReplaysAreIdempotent() {
        var channel = dataset(ReconciliationSide.CHANNEL, SCOPE, List.of());
        var ledger = dataset(ReconciliationSide.LEDGER, SCOPE, List.of());
        var init = ReconciliationBatch.init("batch", SCOPE);
        assertEquals(ReconciliationState.INIT, init.state());
        assertThrows(DomainViolation.class, () -> init.begin("run"));
        var first = init.attach(channel.manifest());
        assertSame(first, first.attach(channel.manifest()));
        var ready = first.attach(ledger.manifest());
        assertEquals(ReconciliationState.READY, ready.state());
        assertEquals(2L, ready.version());
        var running = ready.begin("run");
        assertSame(running, running.begin("run"));
        assertThrows(DomainViolation.class, () -> running.begin("different-run"));
        var result = matcher.compare(channel, ledger);
        assertThrows(DomainViolation.class, () -> running.complete("different-run", result));
        var completed = running.complete("run", result);
        assertEquals(ReconciliationState.MATCHED, completed.state());
        assertEquals(4L, completed.version());
        assertSame(completed, completed.complete("run", result));
        assertThrows(DomainViolation.class, () -> completed.begin("new-run"));
        assertThrows(DomainViolation.class, () -> completed.attach(channel.manifest()));
        var changed = new ReconciliationManifest(ReconciliationSide.CHANNEL, SCOPE, "new-artifact", "f".repeat(64), channel.manifest().normalizedSha256(), "new-capture", "parser-v1", channel.manifest().totals());
        assertThrows(DomainViolation.class, () -> first.attach(changed));
        assertThrows(DomainViolation.class, () -> running.complete("run", matcher.compare(new ReconciliationDataset(changed, List.of()), ledger)));
    }
}
