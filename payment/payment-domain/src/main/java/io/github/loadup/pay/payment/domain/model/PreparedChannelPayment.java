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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.payment.PaymentMethod;

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record PreparedChannelPayment(
        ChannelPaymentIdentity identity,
        Money amount,
        PaymentMethod method,
        long executionEpoch,
        @DiagnosticHidden String preparedInputRef) {
    public PreparedChannelPayment {
        Objects.requireNonNull(identity);
        DomainChecks.cnyAmount(amount);
        Objects.requireNonNull(method);
        require(amount.getCent() > 0 && executionEpoch > 0, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
