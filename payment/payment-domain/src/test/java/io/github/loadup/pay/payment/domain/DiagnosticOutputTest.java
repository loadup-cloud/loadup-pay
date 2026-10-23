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
package io.github.loadup.pay.payment.domain;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.domain.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.PreparedChannelPayment;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticOutputTest {
    private ChannelBindingRevision binding() {
        return new ChannelBindingRevision(
                "binding",
                "merchant",
                Channel.ALIPAY,
                AccessMode.DIRECT,
                "a".repeat(64),
                "credential-reference-must-stay-hidden",
                "v1",
                new io.github.loadup.pay.payment.domain.model.AlipayAccountIdentity("app", "seller", null, null, null),
                Set.of(PaymentMethod.H5));
    }

    @Test
    void domainRecordsUseBoundedJsonAndRedactNestedSensitiveReferences() {
        var binding = binding();
        var identity =
                new ChannelPaymentIdentity("payment", "attempt", Channel.ALIPAY, "binding", "account-hash", "request");
        var prepared = new PreparedChannelPayment(
                identity, Money.ofMinor(100, "CNY"), PaymentMethod.H5, 1, "prepared-input-must-stay-hidden");
        assertTrue(binding.toString().startsWith("{"));
        assertTrue(binding.toString().contains("\"credentialReference\":\"******\""));
        assertTrue(prepared.toString().contains("\"preparedInputRef\":\"******\""));
        var nested = ToStringAsJson.reflectionToString(List.of(prepared, binding));
        assertFalse(nested.contains("credential-reference-must-stay-hidden"));
        assertFalse(nested.contains("prepared-input-must-stay-hidden"));
    }

    @Test
    void integrationEnvelopeKeepsJsonHeadersWithoutDisclosingPayload() {
        var event = new EventEnvelope<>(
                "delivery",
                "event",
                "payment",
                "pay.payment.snapshot.realtime.v1",
                1,
                "payment",
                1,
                OffsetDateTime.parse("2026-10-21T02:00:00Z"),
                binding());
        var json = event.toString();
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"producer\":\"payment\""));
        assertTrue(json.contains("\"data\":\"******\""));
        assertFalse(json.contains("credential-reference-must-stay-hidden"));
        assertFalse(ToStringAsJson.reflectionToString(List.of(event)).contains("account-hash"));
    }
}
