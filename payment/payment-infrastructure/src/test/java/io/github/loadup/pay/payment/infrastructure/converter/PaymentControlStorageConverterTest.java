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

import io.github.loadup.pay.payment.domain.model.PaymentControlReceipt;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class PaymentControlStorageConverterTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentControlStorageConverter.class.getPackageName());
    final PaymentControlStorageConverter converter = context.getBean(PaymentControlStorageConverter.class);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void closeReceiptRoundTripRetainsItsOriginalProofEpochAndCaller() {
        var receipt = new PaymentControlReceipt(
                "receipt",
                "PAYMENT_CLOSE",
                "payment",
                "order",
                "merchant",
                "acquire",
                "close",
                "a".repeat(64),
                2,
                "CANCELLED",
                "proof");
        var row = converter.toReceiptDO("tenant", receipt);
        assertEquals("tenant", row.getTenantId());
        assertEquals(receipt, converter.toReceipt(row));
    }

    @Test
    void parentReceiptRoundTripNeverInventsANoCollectionEvidence() {
        var receipt = new PaymentControlReceipt(
                "receipt",
                "PARENT_FENCE",
                "order",
                "order",
                "merchant",
                "acquire",
                "fence",
                "a".repeat(64),
                3,
                null,
                null);
        var restored = converter.toReceipt(converter.toReceiptDO("tenant", receipt));
        assertEquals(receipt, restored);
        assertNull(restored.noCollectionEvidenceId());
    }
}
