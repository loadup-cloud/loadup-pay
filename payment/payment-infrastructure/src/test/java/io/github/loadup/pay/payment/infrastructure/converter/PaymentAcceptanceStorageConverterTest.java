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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaymentAcceptanceStorageConverterTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentAcceptanceStorageConverter.class.getPackageName());
    final PaymentAcceptanceStorageConverter converter = context.getBean(PaymentAcceptanceStorageConverter.class);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void acceptanceRoundTripKeepsItsFixedPaymentOperationAndFrozenIntent() {
        var payment = PaymentOrder.init(
                "payment",
                "order",
                "allocation",
                "merchant",
                DomainChecks.cnyAmount(4000, "CNY"),
                "operation",
                LocalDateTime.of(2026, 10, 22, 12, 0));
        var value = new StoredPaymentAcceptance(
                payment, "admission", "a".repeat(64), "acquire", "payload", "b".repeat(64), null);
        var row = converter.toPaymentDO("tenant", value);
        assertNull(row.getFirstUnknownAt());
        var firstUnknownAt = payment.initiatedAt().plusMinutes(2);
        assertEquals(firstUnknownAt, converter.toPaymentPatch("tenant", value, firstUnknownAt).getFirstUnknownAt());
        row.setFirstUnknownAt(firstUnknownAt);
        assertEquals("tenant", row.getTenantId());
        assertEquals(value, converter.toStored(row, converter.toPayment(row)));
    }

    @Test
    void parentGateMappingKeepsTheTombstoneAndDoesNotReopenIt() {
        var value = new StoredPaymentGate(
                "gate",
                "merchant",
                "acquire",
                PaymentExecutionGate.init("order", null).fence(3),
                0);
        var row = converter.toParentDO("tenant", value);
        assertEquals(3L, row.getCloseEpoch());
        assertEquals(value, converter.toStoredParent(row, converter.toParentGate(row)));
    }

    @Test
    void paymentGateMappingRetainsThePaymentBindingAndFenceEpoch() {
        var value = new StoredPaymentGate(
                "gate",
                "merchant",
                "acquire",
                PaymentExecutionGate.init("order", "payment").fence(2),
                0);
        var row = converter.toGateDO("tenant", value);
        assertEquals("payment", row.getPaymentId());
        assertEquals(value, converter.toStoredGate(row, converter.toPaymentGate(row)));
    }
}
