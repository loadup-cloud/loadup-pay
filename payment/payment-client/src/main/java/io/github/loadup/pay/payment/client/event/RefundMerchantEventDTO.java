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

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * A source-committed terminal projection; never a command to change order or refund amounts.
 */
public record RefundMerchantEventDTO(
        @Schema(description = "RefundOrder or RefundRequest") String aggregateType,
        @Schema(description = "Child refund id, absent for a completed request") String refundId,
        @Schema(description = "Original refund request id") String refundRequestId,
        @Schema(description = "Original payment id, absent for a completed request") String paymentId,
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Collecting merchant id") String payeeMerchantId,
        @Schema(description = "Original merchant refund number") String merchantRefundNo,
        @Schema(description = "Fixed child or request amount in CNY minor units") long amountMinor,
        @Schema(description = "CNY") String currency,
        @Schema(description = "Terminal child or request state") String state,
        @Schema(description = "Successful amount of this child or fixed request") long succeededMinor,
        @Schema(description = "Definitively failed amount of this child or fixed request") long failedMinor,
        @Schema(description = "Source aggregate version") long sourceVersion,
        @Schema(description = "Verified child success time; absent for failed children and request completion") OffsetDateTime succeededAt) {
    public static final String TYPE = "pay.payment.refund.merchant.v1";

    public RefundMerchantEventDTO {
        DomainChecks.id(refundRequestId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(merchantRefundNo, 64);
        DomainChecks.cnyAmount(amountMinor, currency);
        require(amountMinor > 0 && sourceVersion > 0 && succeededMinor >= 0 && failedMinor >= 0
                && succeededMinor <= amountMinor && failedMinor == amountMinor - succeededMinor, INVALID_ARGUMENT);
        require(aggregateType != null && state != null, INVALID_ARGUMENT);
        if ("RefundOrder".equals(aggregateType)) {
            DomainChecks.id(refundId);
            DomainChecks.id(paymentId);
            require(Set.of("SUCCEEDED", "FAILED").contains(state), INVALID_STATE);
            require(("SUCCEEDED".equals(state)) == (succeededAt != null), INVALID_STATE);
        } else {
            require("RefundRequest".equals(aggregateType) && refundId == null && paymentId == null && succeededAt == null, INVALID_ARGUMENT);
            require(Set.of("SUCCEEDED", "FAILED", "PARTIAL_SUCCEEDED").contains(state), INVALID_STATE);
        }
        require(!"SUCCEEDED".equals(state) || succeededMinor == amountMinor, CONFLICT);
        require(!"FAILED".equals(state) || failedMinor == amountMinor, CONFLICT);
        require(!"PARTIAL_SUCCEEDED".equals(state) || succeededMinor > 0 && failedMinor > 0, CONFLICT);
        if (succeededAt != null) {
            succeededAt = succeededAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    public String aggregateId() {
        return refundId == null ? refundRequestId : refundId;
    }

    public String merchantEventType() {
        return "RefundRequest".equals(aggregateType) ? "RefundRequestCompleted" : "SUCCEEDED".equals(state) ? "RefundSucceeded" : "RefundFailed";
    }

    public void requireEnvelope(EventEnvelope<?> event) {
        require(event != null && equals(event.data()), CONFLICT);
        event.requireSource(TYPE, "payment", aggregateId(), sourceVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
