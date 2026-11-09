/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.reconciliation.client.dto;

import static io.github.loadup.reconciliation.client.support.ReconciliationContracts.hash;
import static io.github.loadup.reconciliation.client.support.ReconciliationContracts.require;
import static io.github.loadup.reconciliation.client.support.ReconciliationContracts.text;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.reconciliation.client.enums.ReconciliationSide;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReconciliationInputDTO(
        @Schema(description = "Evidence side") ReconciliationSide side,

        @Schema(description = "Immutable sealed artifact id")
        String artifactId,

        @Schema(description = "Original file SHA-256", minLength = 64, maxLength = 64)
        String artifactSha256,

        @Schema(description = "Versioned normalized-record SHA-256", minLength = 64, maxLength = 64)
        String normalizedSha256,

        @Schema(description = "Validated source capture or export id")
        String sourceCaptureId,

        @Schema(description = "Frozen parser and normalization revision")
        String parserRevision,

        @Schema(description = "Normalized payment record count")
        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        long paymentCount,

        @Schema(description = "Gross payment total in CNY minor units; not net of refunds")
        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        long paymentAmountMinor,

        @Schema(description = "Normalized refund record count")
        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        long refundCount,

        @Schema(description = "Gross refund request total in CNY minor units; does not assert returned funds")
        @JsonProperty(required = true)
        @JsonSetter(nulls = Nulls.FAIL)
        long refundAmountMinor) {
    public ReconciliationInputDTO {
        require(side != null);
        text(artifactId, 64);
        text(sourceCaptureId, 64);
        text(parserRevision, 64);
        hash(artifactSha256);
        hash(normalizedSha256);
        require(paymentCount >= 0
                && refundCount >= 0
                && paymentAmountMinor >= paymentCount
                && refundAmountMinor >= refundCount
                && (paymentCount == 0) == (paymentAmountMinor == 0)
                && (refundCount == 0) == (refundAmountMinor == 0));
        try {
            Math.addExact(paymentCount, refundCount);
        } catch (ArithmeticException invalid) {
            require(false);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
