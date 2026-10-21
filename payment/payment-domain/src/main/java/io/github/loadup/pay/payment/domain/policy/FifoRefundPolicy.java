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
package io.github.loadup.pay.payment.domain.policy;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.domain.model.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** The application persists this result and all capacity reservations in one transaction. */
public final class FifoRefundPolicy {
    public List<RefundPlanItem> plan(
            String orderId,
            String payeeMerchantId,
            Money amount,
            boolean partialAllowed,
            List<PaymentRefundCapacity> candidates) {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        require(amount.minor() > 0, INVALID_ARGUMENT);
        var sorted = candidates.stream()
                .sorted(Comparator.comparing(PaymentRefundCapacity::succeededAt)
                        .thenComparing(PaymentRefundCapacity::paymentId))
                .toList();
        Set<String> seen = new HashSet<>();
        long available = 0;
        for (var capacity : sorted) {
            require(
                    capacity.orderId().equals(orderId)
                            && capacity.payeeMerchantId().equals(payeeMerchantId)
                            && capacity.succeededAmount().currency().equals(amount.currency())
                            && seen.add(capacity.paymentId()),
                    CONFLICT);
            available = Math.addExact(available, capacity.availableMinor());
            if (!partialAllowed) require(capacity.reservedMinor() == 0, CONFLICT);
        }
        require(amount.minor() <= available && (partialAllowed || amount.minor() == available), AMOUNT_EXCEEDED);
        List<RefundPlanItem> result = new ArrayList<>();
        long remaining = amount.minor();
        for (var capacity : sorted) {
            long portion = Math.min(remaining, capacity.availableMinor());
            if (portion > 0) {
                result.add(new RefundPlanItem(
                        result.size() + 1, capacity.paymentId(), new Money(portion, amount.currency())));
                remaining -= portion;
            }
            if (remaining == 0) break;
        }
        return List.copyOf(result);
    }
}
