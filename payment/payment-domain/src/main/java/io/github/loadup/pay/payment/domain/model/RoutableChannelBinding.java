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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record RoutableChannelBinding(ChannelMerchantBinding head, StoredChannelBindingRevision revision) {
    public RoutableChannelBinding {
        require(head != null && revision != null && head.state() == ChannelBindingState.ACTIVE
                && head.id().equals(revision.bindingId()) && head.currentRevisionId().equals(revision.binding().id())
                && head.currentRevision() == revision.revision()
                && head.payeeMerchantId().equals(revision.binding().payeeMerchantId())
                && head.channel() == revision.binding().channel() && head.accessMode() == revision.binding().accessMode(), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
