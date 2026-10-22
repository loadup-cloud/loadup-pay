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
package io.github.loadup.pay.payment.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record RoutingProfileSpecDTO(
        @Schema(description = "Immutable version reference; changes require a new reference")
        @NotBlank @Size(max = 128) String profileRef,
        @Schema(description = "Collecting merchant id") @NotBlank @Size(max = 64) String payeeMerchantId,
        @Schema(description = "Ordered candidates; first active matching rule wins")
        @NotEmpty @Size(max = 100) List<@NotNull @Valid RoutingRuleDTO> rules) {
    public RoutingProfileSpecDTO { rules = rules == null ? List.of() : List.copyOf(rules); }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
