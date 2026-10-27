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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.event.EventEnvelope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Complete monetary snapshot, bounded independently of historical refund-request count.
 */
public record OrderRefundSummaryEventDTO(
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Currency") String currency,
        @Schema(description = "Cumulative verified successful refund amount in minor units") long refundedMinor,
        @Schema(description = "Amount held by all unfinished refund children in minor units") long refundReservedMinor,
        @Schema(description = "Independent source monetary summary version") long refundSummaryVersion) {
    public static final String REALTIME_TYPE = "pay.payment.refund-summary.realtime.v1";
    public static final String FEE_TYPE = "pay.payment.refund.fee.v1";

    public OrderRefundSummaryEventDTO {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.cnyAmount(refundedMinor, currency);
        require(refundedMinor >= 0 && refundReservedMinor >= 0 && refundReservedMinor <= Long.MAX_VALUE - refundedMinor
                && refundSummaryVersion >= 0, INVALID_ARGUMENT);
    }

    public void requireEnvelope(EventEnvelope<?> envelope) {
        require(equals(envelope.data()) && Set.of(REALTIME_TYPE, FEE_TYPE)
                .contains(envelope.eventType()), CONFLICT);
        envelope.requireSource(envelope.eventType(), "payment", orderId, refundSummaryVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
