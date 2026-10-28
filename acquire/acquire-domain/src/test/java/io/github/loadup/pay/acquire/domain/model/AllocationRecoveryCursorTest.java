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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationRecoveryCursorTest {
    @Test
    void generationAndDeadlineAdvanceTogetherAndCompletionIsPermanent() {
        var at = LocalDateTime.of(2026, 10, 26, 12, 0);
        var initial = AllocationRecoveryCursor.init("payment", "order", at);
        var next = initial.schedule(at.plusSeconds(30), 0L);
        assertEquals("payment", next.paymentId());
        assertEquals("order", next.orderId());
        assertEquals(1L, next.generation());
        assertThrows(DomainViolation.class, () -> initial.schedule(at, 0L));
        assertThrows(DomainViolation.class, () -> next.schedule(at.plusMinutes(1), 0L));
        var completed = next.finish();
        assertTrue(completed.completed());
        assertSame(completed, completed.finish());
        assertThrows(DomainViolation.class, () -> completed.schedule(at.plusMinutes(1), 1L));
    }

    @Test
    void onlyCanonicalBusinessKeysCanSelectAnOriginalGeneration() {
        assertEquals(new AllocationRecoveryKey("payment", 12L), AllocationRecoveryKey.parse("payment.12"));
        for (var invalid : List.of("payment", "payment.", ".0", "payment.-1", "payment.+1", "payment.01",
                "payment. 1", "payment.9223372036854775808")) {
            assertThrows(DomainViolation.class, () -> AllocationRecoveryKey.parse(invalid));
        }
        assertThrows(DomainViolation.class, () -> AllocationRecoveryKey.parse(null));
    }
}
