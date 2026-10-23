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
import io.github.loadup.pay.common.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

public record RefundExecutionFacts(StoredRefundChild child, LocalDateTime submissionStartedAt,
                                   String channelRefundTransactionNo, LocalDateTime succeededAt) {
    public RefundExecutionFacts {
        Objects.requireNonNull(child);
        require((child.refund().state() == RefundState.INIT) == (submissionStartedAt == null), INVALID_ARGUMENT);
        if (child.refund().state() == RefundState.SUCCEEDED) {
            DomainChecks.text(channelRefundTransactionNo, 128);
            Objects.requireNonNull(succeededAt);
            require(!succeededAt.isBefore(child.originalSucceededAt()), INVALID_ARGUMENT);
        } else require(channelRefundTransactionNo == null && succeededAt == null, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
