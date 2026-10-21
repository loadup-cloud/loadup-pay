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
package io.github.loadup.pay.product.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/** A successful customer call returns authoritative facts; dependency failure must propagate. */
public record MerchantEligibilityFacts(
        String payeeMerchantId, boolean eligible, String reasonCode, long merchantVersion, long authorizationVersion) {
    public MerchantEligibilityFacts {
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(reasonCode, 64);
        require(merchantVersion >= 0 && authorizationVersion >= 0, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
