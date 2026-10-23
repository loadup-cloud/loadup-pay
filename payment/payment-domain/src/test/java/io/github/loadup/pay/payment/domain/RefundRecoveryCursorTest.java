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
import io.github.loadup.pay.payment.domain.model.RefundRecoveryCursor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundRecoveryCursorTest {
    final LocalDateTime dueAt = LocalDateTime.parse("2026-10-23T12:00:00");

    @Test
    void initializesAndSchedulesOnlyTheExpectedGenerationAtALaterTime() {
        var initial = RefundRecoveryCursor.init("request", dueAt);
        assertEquals(0, initial.generation());
        assertFalse(initial.completed());
        assertEquals(dueAt, initial.dueAt());

        var nextAt = dueAt.plusSeconds(30);
        var next = initial.schedule(nextAt, 0);
        assertEquals("request", next.requestId());
        assertEquals(1, next.generation());
        assertEquals(nextAt, next.dueAt());
        assertFalse(next.completed());
    }

    @Test
    void rejectsWrongGenerationAndNonLaterDueTimes() {
        var cursor = RefundRecoveryCursor.init("request", dueAt);
        assertThrows(DomainViolation.class, () -> cursor.schedule(dueAt.plusSeconds(1), 1));
        assertThrows(DomainViolation.class, () -> cursor.schedule(dueAt, 0));
        assertThrows(DomainViolation.class, () -> cursor.schedule(dueAt.minusSeconds(1), 0));
    }

    @Test
    void finishingKeepsTheGenerationAndDueTimeAndIsIdempotent() {
        var cursor = RefundRecoveryCursor.init("request", dueAt).schedule(dueAt.plusSeconds(30), 0);
        var finished = cursor.finish();
        assertTrue(finished.completed());
        assertEquals(cursor.generation(), finished.generation());
        assertEquals(cursor.dueAt(), finished.dueAt());
        assertEquals(finished, finished.finish());
        assertThrows(DomainViolation.class, () -> finished.schedule(finished.dueAt().plusSeconds(30), finished.generation()));
    }
}
