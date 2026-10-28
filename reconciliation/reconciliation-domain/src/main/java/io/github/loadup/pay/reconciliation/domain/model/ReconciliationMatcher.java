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

import java.util.ArrayList;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Pure merge of two complete inputs; no source lookups, clocks, financial writes or partial-window orphan decisions.
 */
public final class ReconciliationMatcher {
    public ReconciliationResult compare(ReconciliationDataset channel, ReconciliationDataset ledger) {
        Objects.requireNonNull(channel);
        Objects.requireNonNull(ledger);
        require(channel.manifest().side() == ReconciliationSide.CHANNEL && ledger.manifest().side() == ReconciliationSide.LEDGER
                && channel.manifest().scope().equals(ledger.manifest().scope()), CONFLICT);
        var differences = new ArrayList<ReconciliationDifference>();
        long matched = 0;
        int c = 0, l = 0;
        while (c < channel.records().size() || l < ledger.records().size()) {
            var fromChannel = c < channel.records().size() ? channel.records().get(c) : null;
            var fromLedger = l < ledger.records().size() ? ledger.records().get(l) : null;
            int order = fromChannel == null ? 1 : fromLedger == null ? -1 : fromChannel.key().compareTo(fromLedger.key());
            var key = order <= 0 ? fromChannel.key() : fromLedger.key();
            if (order < 0) fromLedger = null;
            if (order > 0) fromChannel = null;
            var reasons = ReconciliationDifference.reasons(fromChannel, fromLedger);
            if (reasons.isEmpty()) matched++;
            else differences.add(new ReconciliationDifference(key, fromChannel, fromLedger, reasons));
            if (order <= 0) c++;
            if (order >= 0) l++;
        }
        return new ReconciliationResult(channel.manifest(), ledger.manifest(), matched, differences);
    }
}
