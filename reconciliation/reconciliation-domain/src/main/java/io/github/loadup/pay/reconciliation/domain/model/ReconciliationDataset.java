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
import static io.github.loadup.commons.error.DomainViolation.Reason.*;
import static io.github.loadup.commons.error.DomainViolation.require;
import io.github.loadup.commons.json.DiagnosticHidden;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Complete bounded normalized input. Duplicate keys and repeated source record ordinals invalidate the input. */
public record ReconciliationDataset(ReconciliationManifest manifest, @DiagnosticHidden List<ReconciliationLine> records) {
    public ReconciliationDataset {
        Objects.requireNonNull(manifest);
        records = List.copyOf(records).stream().sorted(Comparator.comparing(ReconciliationLine::key)).toList();
        var numbers = new HashSet<Long>();
        ReconciliationKey previous = null;
        for (var row : records) {
            require(!row.key().equals(previous) && numbers.add(row.recordNumber()), CONFLICT);
            previous = row.key();
        }
        require(manifest.totals().equals(ReconciliationTotals.count(records))
                && manifest.normalizedSha256().equals(normalizedHash(manifest.scope(), manifest.side(), manifest.parserRevision(), records)), CONFLICT);
    }
    public static String normalizedHash(ReconciliationScope scope, ReconciliationSide side, String parserRevision, List<ReconciliationLine> records) {
        var fingerprint = new ReconciliationFingerprint("reconciliation-input-v1");
        fingerprint.scope(scope); fingerprint.field(side.name()); fingerprint.field(parserRevision);
        fingerprint.field(Integer.toString(records.size()));
        records.stream().sorted(Comparator.comparing(ReconciliationLine::key)).forEach(fingerprint::line);
        return fingerprint.finish();
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
