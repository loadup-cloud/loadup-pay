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
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

public record RiskOutcome(
        RiskConsultation consultation,
        RiskDecision decision,
        PaymentState paymentState,
        long sourceVersion,
        String evidenceId) {
    public RiskOutcome {
        Objects.requireNonNull(consultation);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(paymentState);
        require(sourceVersion > 0, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
