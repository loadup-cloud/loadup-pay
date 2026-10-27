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
import java.util.Comparator;
import java.util.List;

/** Exact immutable input manifests and deterministic differences bind completion to this particular comparison. */
public record ReconciliationResult(ReconciliationManifest channel, ReconciliationManifest ledger,
        long matchedCount, List<ReconciliationDifference> differences) {
    public ReconciliationResult {
        require(channel != null && ledger != null && channel.side() == ReconciliationSide.CHANNEL
                && ledger.side() == ReconciliationSide.LEDGER && channel.scope().equals(ledger.scope()), CONFLICT);
        require(!channel.artifactId().equals(ledger.artifactId()) && !channel.sourceCaptureId().equals(ledger.sourceCaptureId()), CONFLICT);
        require(matchedCount >= 0, INVALID_ARGUMENT);
        differences = List.copyOf(differences).stream().sorted(Comparator.comparing(ReconciliationDifference::key)).toList();
        long channelDifferences = 0, ledgerDifferences = 0;
        ReconciliationKey previous = null;
        for (var difference : differences) {
            require(!difference.key().equals(previous), CONFLICT);
            previous = difference.key();
            if (difference.channel() != null) channelDifferences++;
            if (difference.ledger() != null) ledgerDifferences++;
        }
        require(channelDifferences <= channel.totals().recordCount() && ledgerDifferences <= ledger.totals().recordCount()
                && matchedCount == channel.totals().recordCount() - channelDifferences
                && matchedCount == ledger.totals().recordCount() - ledgerDifferences, CONFLICT);
    }
    public ReconciliationResultSummary summary() {
        var fingerprint = new ReconciliationFingerprint("reconciliation-result-v1");
        manifest(fingerprint, channel); manifest(fingerprint, ledger);
        fingerprint.field(Long.toString(matchedCount));
        for (var difference : differences) {
            fingerprint.field(difference.key().kind().name()); fingerprint.field(difference.key().channelTransactionNo());
            fingerprint.field(difference.key().channelRefundNo());
            for (var reason : difference.reasons().stream().sorted().toList()) fingerprint.field(reason.name());
            fingerprint.field("end-reasons"); fingerprint.line(difference.channel()); fingerprint.line(difference.ledger());
        }
        return new ReconciliationResultSummary(matchedCount, differences.size(), fingerprint.finish());
    }
    private static void manifest(ReconciliationFingerprint fingerprint, ReconciliationManifest manifest) {
        fingerprint.field(manifest.side().name()); fingerprint.scope(manifest.scope()); fingerprint.field(manifest.artifactId());
        fingerprint.field(manifest.artifactSha256()); fingerprint.field(manifest.normalizedSha256()); fingerprint.field(manifest.sourceCaptureId());
        fingerprint.field(manifest.parserRevision());
        var totals = manifest.totals();
        fingerprint.field(Long.toString(totals.paymentCount())); fingerprint.field(Long.toString(totals.paymentAmountMinor()));
        fingerprint.field(Long.toString(totals.refundCount())); fingerprint.field(Long.toString(totals.refundAmountMinor()));
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
