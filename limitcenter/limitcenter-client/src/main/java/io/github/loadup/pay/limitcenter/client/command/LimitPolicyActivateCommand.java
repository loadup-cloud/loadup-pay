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
package io.github.loadup.pay.limitcenter.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.client.enums.RefundCountRestorePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LimitPolicyActivateCommand(
        @Schema(description = "Stable policy identifier") @NotBlank @Size(max = 64)
        String policyId,

        @Schema(description = "Consecutive immutable revision, starting at 1") @Positive
        int policyVersion,

        @Schema(description = "Canonical SHA-256 from LimitPolicyHash, bound to the trusted tenant")
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        String policyHash,

        @Schema(description = "Payee merchant owning this policy") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Amount restoration policy for future refund processing") @NotNull
        RefundAmountRestorePolicy refundAmountRestore,

        @Schema(description = "Count restoration policy for future refund processing") @NotNull
        RefundCountRestorePolicy refundCountRestore,

        @Schema(description = "Complete rule set, not a patch; maximum 64") @NotEmpty @Size(max = 64)
        List<@Valid LimitRuleDTO> rules) {
    public LimitPolicyActivateCommand {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
