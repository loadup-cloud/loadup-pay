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
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record PaymentOutcomeEventDTO(
        @Schema(description = "Payment id") String paymentId,
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Allocation id") String allocationId,
        @Schema(description = "Operation id") String operationId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Amount minor") long amountMinor,
        @Schema(description = "Currency") String currency,
        @Schema(description = "State") PaymentState state,
        @Schema(description = "Payment version") long paymentVersion,
        @Schema(description = "Evidence id") String evidenceId,
        @Schema(description = "Execution fenced") boolean executionFenced) {
    public PaymentOutcomeEventDTO {
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(allocationId);
        DomainChecks.id(operationId);
        DomainChecks.id(payeeMerchantId);
        DomainViolation.require(amountMinor > 0 && paymentVersion > 0 && state != null,
                DomainViolation.Reason.INVALID_ARGUMENT);
        new Money(amountMinor, currency);
        if (state == PaymentState.SUCCEEDED || state == PaymentState.FAILED || state == PaymentState.CLOSED)
            DomainChecks.id(evidenceId);
        if (state == PaymentState.FAILED || state == PaymentState.CLOSED)
            DomainViolation.require(executionFenced, DomainViolation.Reason.OUTCOME_UNKNOWN);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
