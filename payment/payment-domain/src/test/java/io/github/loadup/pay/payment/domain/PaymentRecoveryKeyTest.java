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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentRecoveryKeyTest {
    @Test
    void canonicalKeyRoundTripsDottedAndMaximumLengthPaymentIds() {
        var dotted = new PaymentRecoveryKey("payment.part.id", 12);
        assertEquals("payment.part.id.12", dotted.bizId());
        assertEquals(dotted, PaymentRecoveryKey.parse(dotted.bizId()));

        var maximumId = "p".repeat(64);
        var maximum = new PaymentRecoveryKey(maximumId, 0);
        assertEquals(maximum, PaymentRecoveryKey.parse(maximum.bizId()));
    }

    @Test
    void parserRejectsMissingMalformedAndNoncanonicalGenerations() {
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse(null));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse(""));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse("payment"));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse("payment."));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse("payment.-1"));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse("payment.00"));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse("payment.+1"));
        assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse("payment.9223372036854775808"));
        assertThrows(DomainViolation.class, () -> new PaymentRecoveryKey("payment", -1));
    }
}
