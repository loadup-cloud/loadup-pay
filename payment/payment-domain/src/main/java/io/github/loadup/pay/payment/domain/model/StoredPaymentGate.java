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

/**
 * Both gate kinds bind the original order, collecting merchant and trusted service.
 */
public record StoredPaymentGate(
        String id, String payeeMerchantId, String callerServiceId, PaymentExecutionGate gate, long rowVersion) {
    public StoredPaymentGate {
        io.github.loadup.pay.common.DomainViolation.require(
                rowVersion >= 0, io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT);
        DomainChecks.id(id);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(callerServiceId);
        java.util.Objects.requireNonNull(gate);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
