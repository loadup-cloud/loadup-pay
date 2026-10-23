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
import io.github.loadup.pay.payment.domain.model.RefundRecoveryKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundRecoveryKeyTest {
    @Test
    void canonicalKeyRoundTripsDottedAndMaximumLengthRequestIds() {
        var dotted = new RefundRecoveryKey("refund.request.part", 12);
        assertEquals("refund.request.part.12", dotted.bizId());
        assertEquals(dotted, RefundRecoveryKey.parse(dotted.bizId()));

        var maximumId = "r".repeat(64);
        var maximum = new RefundRecoveryKey(maximumId, 0);
        assertEquals(maximum, RefundRecoveryKey.parse(maximum.bizId()));
    }

    @Test
    void parserRejectsMissingMalformedAndNoncanonicalGenerations() {
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse(null));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse(""));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse("request"));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse("request."));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse("request.-1"));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse("request.00"));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse("request.+1"));
        assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse("request.9223372036854775808"));
        assertThrows(DomainViolation.class, () -> new RefundRecoveryKey("request", -1));
    }
}
