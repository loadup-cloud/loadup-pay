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
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryRetryReceipt;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentRecoveryRetryReceiptTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 26, 12, 0);

    PaymentRecoveryRetryReceipt receipt(String kind, long generation, String result, LocalDateTime expectedAt) {
        return new PaymentRecoveryRetryReceipt("receipt", "operation", "a".repeat(64), "pay-ops", "b".repeat(64), kind,
                "original-subject", generation, "order", "task", 5, expectedAt, "Private audited reason", result, at);
    }

    @Test
    void retryDecisionsCannotMasqueradeAsFinancialResultsOrAnotherTasksNamespace() {
        assertEquals("RETRIED", receipt("COLLECTION", 0L, "RETRIED", at).result());
        assertEquals("NO_LONGER_NEEDED", receipt("REFUND_REQUEST", 0L, "NO_LONGER_NEEDED", at).result());
        assertThrows(DomainViolation.class, () -> receipt("ORDER_DEADLINE", 0L, "RETRIED", at));
        assertThrows(DomainViolation.class, () -> receipt("COLLECTION", -1L, "RETRIED", at));
        assertThrows(DomainViolation.class, () -> receipt("REFUND_REQUEST", 0L, "SUCCEEDED", at));
        assertThrows(DomainViolation.class, () -> receipt("COLLECTION", 0L, "RETRIED", at.plusNanos(1)));
        assertFalse(receipt("COLLECTION", 0L, "RETRIED", at).toString().contains("Private audited reason"));
    }
}
