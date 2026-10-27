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
package io.github.loadup.pay.reconciliation.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** No tenant, collecting-account hash, raw URL or completed-input assertions come from this command. */
public record ReconciliationBatchCreateCommand(
        @Schema(description = "Caller idempotency key", example = "reconcile-20261026-store-01") @NotBlank @Size(max = 128) String requestKey,
        @Schema(description = "Trusted collecting-account catalog code", example = "merchant-wechat-direct") @NotBlank @Size(max = 64) String channelAccountCode,
        @Schema(description = "Channel statement date", example = "2026-10-26", format = "date") @NotNull LocalDate billDate) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
