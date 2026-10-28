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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/**
 * Missing input is not evidence that no payment exists or can be accepted later.
 */
public record PaymentInputLookupDTO(
        @Schema(description = "Whether the exact original input was observed") @JsonInclude(JsonInclude.Include.ALWAYS) boolean found,
        @Schema(description = "Original opaque reference, fingerprint and expiry; absent only when found is false") @Valid PaymentInputDTO input) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
