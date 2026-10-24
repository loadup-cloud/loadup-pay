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
package io.github.loadup.pay.product.client;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.enums.FeeRefundPolicy;
import io.github.loadup.pay.product.client.enums.UnpaidOrderAction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcquiringTermsTest {

    @Test
    void extensionBudgetAndPaymentRangeMustBeInternallyConsistent() {
        assertThrows(DomainViolation.class, () -> new OrderTermsDTO(60, 30, UnpaidOrderAction.CLOSE, false, 0, 30));
        assertThrows(DomainViolation.class, () -> new OrderTermsDTO(30, 60, UnpaidOrderAction.CLOSE, false, 60, 30));
        assertThrows(DomainViolation.class, () -> new PaymentTermsDTO(100, 10, null, true));
        assertDoesNotThrow(() -> new OrderTermsDTO(30, 60, UnpaidOrderAction.KEEP_PENDING, true, 60, 30));
    }

    @Test
    void configuredFeeBoundsAndNotificationIdentityMustBeUsable() {
        assertThrows(
                DomainViolation.class,
                () -> new FeeTermsDTO(
                        new BigDecimal("0.006"), 0, RoundingMode.HALF_UP, 10, 5L, FeeRefundPolicy.NO_RETURN));
        assertThrows(
                DomainViolation.class,
                () -> new NotificationTermsDTO(true, null, null, 3000, 1, BigDecimal.valueOf(2), 60, 10, 3600));
        assertThrows(
                DomainViolation.class,
                () -> new NotificationTermsDTO(true, "target", "signer", 3000, 1, new BigDecimal("0.5"), 60, 10, 3600));
        assertDoesNotThrow(
                () -> new NotificationTermsDTO(false, null, null, 3000, 1, BigDecimal.valueOf(2), 60, 10, 3600));
        assertThrows(DomainViolation.class, () -> new NotificationTermsDTO(true, "target", "signer", 3000, 1,
                new BigDecimal("1.0000000000000000001"), 60, 10, 3600));
        assertThrows(DomainViolation.class, () -> new NotificationTermsDTO(true, "target", "signer", 3000, 1,
                new BigDecimal("100000000000000000000"), 60, 10, 3600));
    }
}
