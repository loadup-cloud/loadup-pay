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
package io.github.loadup.pay.product.domain;

import io.github.loadup.commons.contract.ContractReference;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import io.github.loadup.pay.product.domain.model.ContractAdmissionState;
import io.github.loadup.pay.product.domain.model.MerchantContractFacts;
import io.github.loadup.pay.product.domain.model.MerchantEligibilityFacts;
import io.github.loadup.pay.product.domain.model.OrderAdmission;
import io.github.loadup.pay.product.domain.policy.OrderAdmissionPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAdmissionTest {
    @Test
    void currentQualificationAndOriginalAdmissionAreDifferentFacts() {
        var start = LocalDateTime.of(2026, 10, 21, 10, 0);
        var reference = new ContractReference("contract", 1, 1, "snapshot", "compiled");
        var current = new MerchantContractFacts(
                "merchant", reference, ContractAdmissionState.SUSPENDED, start.minusDays(1), null, true);
        assertFalse(current.admitsNewOrder(start));
        var original = new OrderAdmission(
                "admission",
                "order",
                "merchant",
                "digest",
                AdmissionDecision.ADMITTED,
                "ALLOW",
                reference,
                start,
                start.plusMinutes(5));
        assertTrue(original.canMaterialize(start.plusMinutes(1)));
        assertThrows(DomainViolation.class, () -> original.requireSameIntent("another-order", "merchant", "digest"));
    }

    @Test
    void newAdmissionRequiresMerchantCurrentContractAndActivatedLimits() {
        var now = LocalDateTime.of(2026, 10, 21, 10, 0);
        var reference = new ContractReference("contract", 1, 1, "snapshot", "compiled");
        var eligible = new MerchantEligibilityFacts("merchant", true, "ALLOW", 1, 2);
        var policy = new OrderAdmissionPolicy();
        var current = new MerchantContractFacts(
                "merchant", reference, ContractAdmissionState.ACTIVE, now.minusDays(1), now.plusDays(1), true);
        assertEquals(
                AdmissionDecision.ADMITTED,
                policy.authorize("admission", "order", "merchant", "digest", current, eligible, now, now.plusMinutes(5))
                        .decision());
        var notActivated = new MerchantContractFacts(
                "merchant", reference, ContractAdmissionState.ACTIVE, now.minusDays(1), null, false);
        assertEquals(
                AdmissionDecision.DENIED,
                policy.authorize(
                                "admission",
                                "order",
                                "merchant",
                                "digest",
                                notActivated,
                                eligible,
                                now,
                                now.plusMinutes(5))
                        .decision());
        var denied = policy.authorize(
                "admission",
                "order",
                "merchant",
                "digest",
                current,
                new MerchantEligibilityFacts("merchant", false, "MERCHANT_SUSPENDED", 2, 2),
                now,
                now.plusMinutes(5));
        assertEquals("MERCHANT_SUSPENDED", denied.reasonCode());
        assertNull(denied.contract());
        assertThrows(
                NullPointerException.class,
                () -> policy.authorize(
                        "admission", "order", "merchant", "digest", current, null, now, now.plusMinutes(5)));
    }
}
