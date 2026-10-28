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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * One complete bounded read-only RR result, or a blocker sample. A blocked result is never a partial successful export.
 */
public record PaymentCaptureRead(PaymentCaptureScope scope, LocalDateTime startedAt, LocalDateTime finishedAt,
                                 @DiagnosticHidden List<PaymentLedgerLine> lines, PaymentCaptureBlocker blocker,
                                 String blockerSubjectId) {
    public PaymentCaptureRead {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(finishedAt);
        require(!finishedAt.isBefore(startedAt), INVALID_ARGUMENT);
        lines = List.copyOf(lines);
        require((blocker == null) == (blockerSubjectId == null), INVALID_ARGUMENT);
        if (blocker != null) {
            require(lines.isEmpty(), INVALID_ARGUMENT);
            DomainChecks.id(blockerSubjectId);
        }
        var keys = new HashSet<Key>();
        long ordinal = 0;
        for (var line : lines) {
            require(line.ordinal() == ++ordinal && !line.sourceAt().isBefore(scope.windowStart()) && line.sourceAt().isBefore(scope.windowEnd()), CONFLICT);
            require(keys.add(new Key(line.kind(), line.channelTransactionNo(), line.channelRefundNo())), CONFLICT);
        }
    }

    public long paymentCount() {
        return lines.stream().filter(line -> line.kind() == PaymentLedgerKind.PAYMENT).count();
    }

    public long refundCount() {
        return lines.size() - paymentCount();
    }

    public long paymentAmountMinor() {
        return total(PaymentLedgerKind.PAYMENT);
    }

    public long refundAmountMinor() {
        return total(PaymentLedgerKind.REFUND);
    }

    private long total(PaymentLedgerKind kind) {
        long total = 0;
        for (var line : lines) if (line.kind() == kind) total = Math.addExact(total, line.amount().getCent());
        return total;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    private record Key(PaymentLedgerKind kind, String transaction, String refund) {
    }
}
