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
package io.github.loadup.pay.payment.client.event;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.dto.RefundRequestSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record OrderRefundSummaryEventDTO(
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Currency") String currency,
        @Schema(description = "Refunded minor") long refundedMinor,
        @Schema(description = "Refund reserved minor") long refundReservedMinor,
        @Schema(description = "Refund summary version") long refundSummaryVersion,

        @Schema(description = "Complete refund request progress snapshots") @Valid
        List<RefundRequestSummaryDTO> requests) {
    public OrderRefundSummaryEventDTO {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainViolation.require(
                refundedMinor >= 0 && refundReservedMinor >= 0 && refundSummaryVersion >= 0,
                DomainViolation.Reason.INVALID_ARGUMENT);
        DomainChecks.amount(refundedMinor, currency);
        requests = List.copyOf(requests);
        var ids = new HashSet<String>();
        long succeeded = 0;
        long reserved = 0;
        for (var request : requests) {
            DomainViolation.require(ids.add(request.refundRequestId()), DomainViolation.Reason.CONFLICT);
            succeeded = Math.addExact(succeeded, request.succeededMinor());
            reserved = Math.addExact(reserved, request.reservedMinor());
        }
        DomainViolation.require(
                succeeded == refundedMinor && reserved == refundReservedMinor, DomainViolation.Reason.CONFLICT);
    }

    public void requireEnvelope(PayEventEnvelope<?> envelope) {
        DomainViolation.require(
                equals(envelope.data())
                        && Set.of("pay.payment.refund-summary.realtime.v1", "pay.payment.refund.fee.v1")
                                .contains(envelope.eventType()),
                DomainViolation.Reason.CONFLICT);
        envelope.requireSource(envelope.eventType(), "payment", orderId, refundSummaryVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
