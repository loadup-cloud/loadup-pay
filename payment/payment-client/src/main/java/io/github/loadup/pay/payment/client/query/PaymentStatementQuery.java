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
package io.github.loadup.pay.payment.client.query;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Privileged source read. The consumer freezes its import intent before requesting this download.
 */
public record PaymentStatementQuery(
        @NotBlank @Size(max = 64) String bindingRevisionId,
        @NotNull Channel channel,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String accountScopeHash,
        @NotNull LocalDate billDate,
        @NotBlank @Size(max = 64) String statementProfile,
        @Min(1) @Max(16777216) int maxBytes) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
