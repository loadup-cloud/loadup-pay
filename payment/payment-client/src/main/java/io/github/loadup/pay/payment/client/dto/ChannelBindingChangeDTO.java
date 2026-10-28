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
import io.github.loadup.pay.payment.domain.model.ChannelBindingState;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Original applied operation snapshot, not a claim about the current mutable head.
 */
public record ChannelBindingChangeDTO(
        @Schema(description = "Binding id") String bindingId,
        @Schema(description = "Immutable revision id applied by this operation") String bindingRevisionId,
        @Schema(description = "Immutable revision number") int revision,
        @Schema(description = "State applied by this operation") ChannelBindingState state,
        @Schema(description = "Head version applied by this operation") long appliedVersion) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
