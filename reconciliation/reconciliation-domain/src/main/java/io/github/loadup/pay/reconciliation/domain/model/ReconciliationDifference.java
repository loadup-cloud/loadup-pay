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

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Both sides remain immutable evidence. A difference authorizes neither a refund nor a source-state rewrite.
 */
public record ReconciliationDifference(ReconciliationKey key, ReconciliationLine channel, ReconciliationLine ledger,
                                       Set<ReconciliationDifferenceReason> reasons) {
    public ReconciliationDifference {
        Objects.requireNonNull(key);
        require(channel != null || ledger != null, INVALID_ARGUMENT);
        require((channel == null || channel.key().equals(key)) && (ledger == null || ledger.key().equals(key)), CONFLICT);
        reasons = Set.copyOf(reasons);
        require(!reasons.isEmpty() && reasons.equals(reasons(channel, ledger)), CONFLICT);
    }

    static Set<ReconciliationDifferenceReason> reasons(ReconciliationLine channel, ReconciliationLine ledger) {
        var result = EnumSet.noneOf(ReconciliationDifferenceReason.class);
        if (channel == null) {
            result.add(ReconciliationDifferenceReason.LEDGER_ONLY);
        } else if (ledger == null) {
            result.add(ReconciliationDifferenceReason.CHANNEL_ONLY);
        } else {
            if (!channel.amount().equals(ledger.amount())) {
                result.add(ReconciliationDifferenceReason.AMOUNT_MISMATCH);
            }
            if (!channel.merchantPaymentNo().equals(ledger.merchantPaymentNo())) {
                result.add(ReconciliationDifferenceReason.REQUEST_MISMATCH);
            }
            if (channel.status() != ledger.status()) {
                result.add(ReconciliationDifferenceReason.STATE_MISMATCH);
            }
        }
        return Set.copyOf(result);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
