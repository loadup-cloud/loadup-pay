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

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Refunds use the original transaction plus original merchant child-refund request, not a guessed universal refund id.
 */
public record ReconciliationKey(ReconciliationRecordKind kind, @DiagnosticHidden String channelTransactionNo,
                                @DiagnosticHidden String channelRefundNo) implements Comparable<ReconciliationKey> {
    public ReconciliationKey {
        Objects.requireNonNull(kind);
        DomainChecks.text(channelTransactionNo, 128);
        require((kind == ReconciliationRecordKind.REFUND) == (channelRefundNo != null), INVALID_ARGUMENT);
        if (channelRefundNo != null) {
            DomainChecks.text(channelRefundNo, 64);
        }
    }

    public String fingerprint() {
        var digest = new ReconciliationFingerprint("reconciliation-key-v1");
        digest.field(kind.name());
        digest.field(channelTransactionNo);
        digest.field(channelRefundNo);
        return digest.finish();
    }

    @Override
    public int compareTo(ReconciliationKey other) {
        int result = kind.name().compareTo(other.kind.name());
        if (result == 0) {
            result = channelTransactionNo.compareTo(other.channelTransactionNo);
        }
        if (result == 0 && channelRefundNo != null) {
            result = channelRefundNo.compareTo(other.channelRefundNo);
        }
        return result;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
