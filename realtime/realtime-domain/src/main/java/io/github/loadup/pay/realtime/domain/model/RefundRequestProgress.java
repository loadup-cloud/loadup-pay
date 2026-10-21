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
package io.github.loadup.pay.realtime.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.pay.common.DomainViolation.require;

public record RefundRequestProgress(
        String refundRequestId,
        String merchantRefundNo,
        long amountMinor,
        String state,
        long succeededMinor,
        long failedMinor,
        int unknownCount,
        long version) {
    public RefundRequestProgress {
        DomainChecks.id(refundRequestId);
        DomainChecks.text(merchantRefundNo, 64);
        require(
                amountMinor > 0
                        && succeededMinor >= 0
                        && failedMinor >= 0
                        && succeededMinor <= amountMinor
                        && failedMinor <= amountMinor - succeededMinor
                        && unknownCount >= 0
                        && version >= 0
                        && state != null,
                INVALID_ARGUMENT);
        switch (state) {
            case "INIT" -> require(succeededMinor == 0 && failedMinor == 0 && unknownCount == 0, INVALID_STATE);
            case "PROCESSING" -> require(succeededMinor + failedMinor < amountMinor, INVALID_STATE);
            case "SUCCEEDED" -> require(succeededMinor == amountMinor && unknownCount == 0, INVALID_STATE);
            case "FAILED" -> require(failedMinor == amountMinor && unknownCount == 0, INVALID_STATE);
            case "PARTIAL_SUCCEEDED" ->
                require(
                        succeededMinor > 0
                                && failedMinor > 0
                                && succeededMinor + failedMinor == amountMinor
                                && unknownCount == 0,
                        INVALID_STATE);
            default -> throw new io.github.loadup.pay.common.DomainViolation(INVALID_ARGUMENT);
        }
    }

    public long reservedMinor() {
        return amountMinor - succeededMinor - failedMinor;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
