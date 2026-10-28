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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationEntryStatus;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationMatcher;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReconciliationStorageConverterTest {
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ReconciliationStorageConverter.class.getPackageName());
    private final ReconciliationStorageConverter values = context.getBean(ReconciliationStorageConverter.class);
    private final ReconciliationDifferenceStorageConverter differences = context.getBean(ReconciliationDifferenceStorageConverter.class);
    private final ReconciliationScope scope = new ReconciliationScope(Channel.ALIPAY, "a".repeat(64), LocalDate.of(2026, 10, 26), "trade-v1");

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void persistedIndependentRefundRetainsExactAmountKeyAndProcessingState() {
        var line = line(9_007_199_254_740_993L, "request", ReconciliationEntryStatus.PROCESSING);
        var row = values.toEntry("tenant", "input", line);
        assertEquals(line.key().fingerprint(), row.getMatchKeyHash());
        assertEquals(line, values.toLine(row));
        var input = manifest(ReconciliationSide.CHANNEL, line);
        var header = values.toInput("tenant", "file", input);
        assertEquals(Long.valueOf(0L), header.getRowVersion());
        assertEquals("SEALED", header.getState());
        assertEquals(input, values.toManifest(header));
    }

    @Test
    void batchStorageRetainsAbsentInputsThenOriginalRunAndResult() {
        var initial = ReconciliationBatch.init("batch", scope);
        var stored = new StoredReconciliationBatch(initial, "ops", "request", "b".repeat(64), "account");
        var row = values.toDO("tenant", stored);
        assertNull(row.getMatchedCount());
        assertNull(row.getDifferenceCount());
        assertNull(row.getRunId());
        assertEquals(initial, values.toBatch(row, null, null, null));
        var line = line(100L, "request", ReconciliationEntryStatus.SUCCEEDED);
        var channel = new ReconciliationDataset(manifest(ReconciliationSide.CHANNEL, line), List.of(line));
        var ledger = new ReconciliationDataset(manifest(ReconciliationSide.LEDGER, line), List.of(line));
        var complete = initial.attach(channel.manifest()).attach(ledger.manifest()).begin("run").complete("run", new ReconciliationMatcher().compare(channel, ledger));
        row = values.toDO("tenant", new StoredReconciliationBatch(complete, "ops", "request", "b".repeat(64), "account"));
        assertEquals(Long.valueOf(0), row.getDifferenceCount());
        assertEquals(complete, values.toBatch(row, channel.manifest(), ledger.manifest(), values.toSummary(row)));
    }

    @Test
    void allMismatchFlagsSurviveStorageAndCorruptFlagsFailClosed() {
        var channel = line(100L, "request", ReconciliationEntryStatus.SUCCEEDED);
        var ledger = line(90L, "other-request", ReconciliationEntryStatus.PROCESSING);
        var result = new ReconciliationMatcher().compare(
                new ReconciliationDataset(manifest(ReconciliationSide.CHANNEL, channel), List.of(channel)),
                new ReconciliationDataset(manifest(ReconciliationSide.LEDGER, ledger), List.of(ledger)));
        var value = result.differences().getFirst();
        var row = differences.toDO("tenant", "batch", "run", value, "channel-entry", "ledger-entry");
        assertEquals(value, differences.toDifference(row, channel, ledger));
        row.setAmountMismatch(2);
        assertThrows(CommonException.class, () -> differences.toDifference(row, channel, ledger));
    }

    private ReconciliationLine line(long amount, String request, ReconciliationEntryStatus state) {
        return new ReconciliationLine(new ReconciliationKey(ReconciliationRecordKind.REFUND, "transaction", "child-refund"), request, Money.ofMinor(amount, "CNY"), state, 1);
    }

    private ReconciliationManifest manifest(ReconciliationSide side, ReconciliationLine line) {
        return new ReconciliationManifest(side, scope, side.name(), "c".repeat(64), ReconciliationDataset.normalizedHash(scope, side, "parser", List.of(line)),
                side.name() + "-capture", "parser", ReconciliationTotals.count(List.of(line)));
    }
}
