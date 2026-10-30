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
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

public record PaymentCapture(String id, String callerServiceId, String requestKey, String requestDigest,
                             PaymentCaptureScope scope,
                             PaymentCaptureState state, LocalDateTime startedAt, LocalDateTime finishedAt,
                             Long paymentCount, Long paymentAmountMinor,
                             Long refundCount, Long refundAmountMinor, String contentHash,
                             PaymentCaptureBlocker blocker, String blockerSubjectId, long version) {
    public PaymentCapture {
        DomainChecks.id(id);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(state);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}") && version == (state == PaymentCaptureState.INIT ? 0L : 1L), INVALID_ARGUMENT);
        boolean initial = state == PaymentCaptureState.INIT, sealed = state == PaymentCaptureState.SEALED;
        require(initial == (startedAt == null) && initial == (finishedAt == null), INVALID_STATE);
        if (!initial) {
            require(!finishedAt.isBefore(startedAt), INVALID_ARGUMENT);
        }
        require(sealed == (paymentCount != null) && sealed == (paymentAmountMinor != null) && sealed == (refundCount != null)
                && sealed == (refundAmountMinor != null) && sealed == (contentHash != null), INVALID_STATE);
        require((state == PaymentCaptureState.BLOCKED) == (blocker != null) && (blocker != null) == (blockerSubjectId != null), INVALID_STATE);
        if (blockerSubjectId != null) {
            DomainChecks.id(blockerSubjectId);
        }
        if (sealed) {
            require(paymentCount >= 0 && refundCount >= 0 && paymentAmountMinor >= paymentCount && refundAmountMinor >= refundCount, INVALID_ARGUMENT);
            require((paymentCount == 0) == (paymentAmountMinor == 0) && (refundCount == 0) == (refundAmountMinor == 0), INVALID_ARGUMENT);
            Math.addExact(paymentCount, refundCount);
            require(contentHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        }
    }

    public static PaymentCapture init(String id, String caller, String key, String digest, PaymentCaptureScope scope) {
        return new PaymentCapture(id, caller, key, digest, scope, PaymentCaptureState.INIT, null, null, null, null, null, null, null, null, null, 0L);
    }

    public PaymentCapture complete(PaymentCaptureRead read, String hash) {
        require(state == PaymentCaptureState.INIT && read != null && scope.equals(read.scope()), INVALID_STATE);
        if (read.blocker() != null) {
            require(hash == null, INVALID_ARGUMENT);
            return new PaymentCapture(id, callerServiceId, requestKey, requestDigest, scope, PaymentCaptureState.BLOCKED,
                    read.startedAt(), read.finishedAt(), null, null, null, null, null, read.blocker(), read.blockerSubjectId(), 1L);
        }
        return new PaymentCapture(id, callerServiceId, requestKey, requestDigest, scope, PaymentCaptureState.SEALED,
                read.startedAt(), read.finishedAt(), read.paymentCount(), read.paymentAmountMinor(), read.refundCount(), read.refundAmountMinor(), hash, null, null, 1L);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
