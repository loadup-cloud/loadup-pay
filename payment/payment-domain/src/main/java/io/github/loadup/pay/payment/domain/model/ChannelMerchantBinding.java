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

import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Mutable route head; immutable revisions remain available to existing attempts and refunds.
 */
public record ChannelMerchantBinding(String id, String payeeMerchantId, String bindingCode, Channel channel,
                                     AccessMode accessMode, ChannelBindingState state, String currentRevisionId,
                                     int currentRevision,
                                     int priority, long rowVersion) {
    public ChannelMerchantBinding {
        DomainChecks.id(id);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(bindingCode);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(accessMode);
        Objects.requireNonNull(state);
        require(priority >= 0 && priority <= 1000000 && rowVersion >= 0 && currentRevision >= 0, INVALID_ARGUMENT);
        if (currentRevisionId != null) DomainChecks.id(currentRevisionId);
        require(state == ChannelBindingState.INIT
                ? currentRevisionId == null && currentRevision == 0 && rowVersion == 0
                : currentRevisionId != null && currentRevision > 0 && rowVersion > 0, INVALID_ARGUMENT);
    }

    public ChannelMerchantBinding publish(ChannelBindingRevision revision, int revisionNo, int nextPriority) {
        require(state != ChannelBindingState.REVOKED, INVALID_STATE);
        require(payeeMerchantId.equals(revision.payeeMerchantId()) && channel == revision.channel()
                && accessMode == revision.accessMode() && revisionNo == Math.incrementExact(currentRevision), CONFLICT);
        return new ChannelMerchantBinding(id, payeeMerchantId, bindingCode, channel, accessMode,
                ChannelBindingState.ACTIVE, revision.id(), revisionNo, nextPriority, Math.incrementExact(rowVersion));
    }

    public ChannelMerchantBinding suspend() {
        require(state == ChannelBindingState.ACTIVE || state == ChannelBindingState.SUSPENDED, INVALID_STATE);
        return new ChannelMerchantBinding(id, payeeMerchantId, bindingCode, channel, accessMode,
                ChannelBindingState.SUSPENDED, currentRevisionId, currentRevision, priority, Math.incrementExact(rowVersion));
    }

    public ChannelMerchantBinding revoke() {
        require(state != ChannelBindingState.INIT, INVALID_STATE);
        return new ChannelMerchantBinding(id, payeeMerchantId, bindingCode, channel, accessMode,
                ChannelBindingState.REVOKED, currentRevisionId, currentRevision, priority, Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
