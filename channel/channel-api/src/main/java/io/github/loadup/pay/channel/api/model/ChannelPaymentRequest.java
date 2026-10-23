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
package io.github.loadup.pay.channel.api.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import java.time.OffsetDateTime;
import java.util.Objects;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Business context frozen by the upstream order; lifetime is not recomputed from channel submission time. */
public record ChannelPaymentRequest(PreparedChannelPayment payment, String subject, OffsetDateTime payableUntil,
        @DiagnosticHidden ChannelPaymentInput input) {
    public ChannelPaymentRequest {
        Objects.requireNonNull(payment);
        DomainChecks.text(subject, 256);
        Objects.requireNonNull(payableUntil);
        Objects.requireNonNull(input);
        boolean matches = switch (payment.method()) {
            case PAYMENT_CODE -> input instanceof ChannelPaymentCodeInput;
            case JSAPI -> input instanceof ChannelPayerIdentityInput payer && payer.channel() == payment.identity().channel();
            case H5 -> input instanceof NoChannelInput;
            default -> false;
        };
        require(matches, INVALID_ARGUMENT);
    }

    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}

