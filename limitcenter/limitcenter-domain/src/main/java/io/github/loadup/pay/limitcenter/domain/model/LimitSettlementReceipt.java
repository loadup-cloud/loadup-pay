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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import static io.github.loadup.pay.common.DomainViolation.require;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;

/** One immutable final monetary outcome; alternate delivery ids cannot consume quota twice. */
public record LimitSettlementReceipt(String id, String operationId, String paymentId, String kind,
        String businessEventId, String requestDigest, long paymentVersion, String evidenceId) {
    public LimitSettlementReceipt {
        DomainChecks.id(id); DomainChecks.id(operationId); DomainChecks.id(paymentId);
        DomainChecks.id(businessEventId); DomainChecks.id(evidenceId);
        require(("CONFIRM".equals(kind) || "RELEASE".equals(kind)) && paymentVersion > 0
                && requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
    }
    public void requireSame(String payment, String expectedKind, String digest, long version, String evidence) {
        require(paymentId.equals(payment) && kind.equals(expectedKind) && requestDigest.equals(digest)
                && paymentVersion == version && evidenceId.equals(evidence), CONFLICT);
    }
    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}
