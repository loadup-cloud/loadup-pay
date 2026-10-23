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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

public record AlipayAccountIdentity(String appId, String sellerId, String operatorAppId,
                                    @DiagnosticHidden String authorizationReference,
                                    @DiagnosticHidden String authorizationVersion)
        implements ChannelAccountIdentity {
    public AlipayAccountIdentity {
        DomainChecks.text(appId, 32);
        DomainChecks.text(sellerId, 32);
        if (operatorAppId != null) DomainChecks.text(operatorAppId, 32);
        require((authorizationReference == null) == (authorizationVersion == null), INVALID_ARGUMENT);
        if (authorizationReference != null) {
            DomainChecks.text(authorizationReference, 128);
            DomainChecks.id(authorizationVersion);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
