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
import java.util.Collection;

/** Gross input totals; refund totals do not reduce successful payment totals or assert completed fund returns. */
public record ReconciliationTotals(long paymentCount, long paymentAmountMinor, long refundCount, long refundAmountMinor) {
    public ReconciliationTotals {
        require(paymentCount >= 0 && refundCount >= 0 && paymentAmountMinor >= 0 && refundAmountMinor >= 0, INVALID_ARGUMENT);
        require((paymentCount == 0) == (paymentAmountMinor == 0) && (refundCount == 0) == (refundAmountMinor == 0), INVALID_ARGUMENT);
        require(paymentCount <= Long.MAX_VALUE - refundCount && paymentAmountMinor >= paymentCount
                && refundAmountMinor >= refundCount, INVALID_ARGUMENT);
    }
    public long recordCount() { return paymentCount + refundCount; }
    public static ReconciliationTotals count(Collection<ReconciliationLine> lines) {
        long payments = 0, paymentAmount = 0, refunds = 0, refundAmount = 0;
        try {
            for (var line : lines) {
                if (line.key().kind() == ReconciliationRecordKind.PAYMENT) {
                    payments = Math.incrementExact(payments); paymentAmount = Math.addExact(paymentAmount, line.amount().getCent());
                } else {
                    refunds = Math.incrementExact(refunds); refundAmount = Math.addExact(refundAmount, line.amount().getCent());
                }
            }
        } catch (ArithmeticException overflow) { require(false, INVALID_ARGUMENT); }
        return new ReconciliationTotals(payments, paymentAmount, refunds, refundAmount);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
