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

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

public record LimitPolicyHead(String policyId, String payeeMerchantId, int latestVersion, long rowVersion) {
    public LimitPolicyHead {
        DomainChecks.id(policyId);
        DomainChecks.id(payeeMerchantId);
        require(latestVersion >= 0 && rowVersion >= 0, INVALID_ARGUMENT);
    }

    public LimitPolicyHead advance(ActivatedLimitPolicy policy) {
        require(
                policyId.equals(policy.policyId())
                        && payeeMerchantId.equals(policy.payeeMerchantId())
                        && latestVersion < Integer.MAX_VALUE
                        && policy.policyVersion() == latestVersion + 1,
                CONFLICT);
        return new LimitPolicyHead(policyId, payeeMerchantId, policy.policyVersion(), Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
