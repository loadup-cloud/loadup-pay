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
package io.github.loadup.pay.payment.app.model;

import io.github.loadup.commons.json.ToStringAsJson;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * UNKNOWN row inventory; nominal amount is not a limitcenter-held or channel-balance fact.
 */
public record RecoveryInventoryCell(Kind kind, long unknownCount, long nominalAmountMinor,
                                    LocalDateTime oldestUnknownAt) {
    public RecoveryInventoryCell {
        Objects.requireNonNull(kind);
        require(unknownCount >= 0 && nominalAmountMinor >= 0
                && (unknownCount == 0 ? nominalAmountMinor == 0 && oldestUnknownAt == null
                : nominalAmountMinor > 0 && oldestUnknownAt != null), INVALID_ARGUMENT);
    }

    public static RecoveryInventoryCell empty(Kind kind) {
        return new RecoveryInventoryCell(kind, 0L, 0L, null);
    }

    public RecoveryInventoryCell plus(RecoveryInventoryCell other) {
        require(kind == other.kind, INVALID_ARGUMENT);
        var oldest = oldestUnknownAt == null ? other.oldestUnknownAt : other.oldestUnknownAt == null ? oldestUnknownAt
                : oldestUnknownAt.isBefore(other.oldestUnknownAt) ? oldestUnknownAt : other.oldestUnknownAt;
        return new RecoveryInventoryCell(kind, Math.addExact(unknownCount, other.unknownCount),
                Math.addExact(nominalAmountMinor, other.nominalAmountMinor), oldest);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public enum Kind {PAYMENT, REFUND}
}
