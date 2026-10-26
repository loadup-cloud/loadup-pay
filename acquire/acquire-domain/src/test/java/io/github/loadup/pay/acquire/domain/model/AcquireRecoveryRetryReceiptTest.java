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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.error.DomainViolation;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AcquireRecoveryRetryReceiptTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 26, 12, 0);
    AcquireRecoveryRetryReceipt receipt(String kind, String subject, long generation, String result, LocalDateTime expectedAt) {
        return new AcquireRecoveryRetryReceipt("receipt", "operation", "a".repeat(64), "pay-ops", "b".repeat(64), kind,
                subject, generation, "order", "task", 5, expectedAt, "Audited private reason", result, at);
    }
    @Test
    void onlyNativeRetryDecisionsAndKnownTaskKindsCanEnterTheAuditLedger() {
        assertEquals("RETRIED", receipt("PAYMENT_ALLOCATION", "payment", 0L, "RETRIED", at).result());
        assertEquals("NO_LONGER_NEEDED", receipt("ORDER_DEADLINE", "order", 1L, "NO_LONGER_NEEDED", at).result());
        assertThrows(DomainViolation.class, () -> receipt("UNKNOWN_TASK", "order", 0L, "RETRIED", at));
        assertThrows(DomainViolation.class, () -> receipt("ORDER_DEADLINE", "order", 0L, "RETRIED", at));
        assertThrows(DomainViolation.class, () -> receipt("ORDER_CLOSURE", "another-order", 0L, "RETRIED", at));
        assertThrows(DomainViolation.class, () -> receipt("PAYMENT_ALLOCATION", "payment", 0L, "PAID", at));
        assertThrows(DomainViolation.class, () -> receipt("PAYMENT_ALLOCATION", "payment", 0L, "RETRIED", at.plusNanos(1)));
        assertFalse(receipt("ORDER_CLOSURE", "order", 0L, "RETRIED", at).toString().contains("Audited private reason"));
    }
}
