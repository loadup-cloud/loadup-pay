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
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryPositionTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    final RecoveryTaskKey key = new RecoveryTaskKey("subject", 0L);
    final RecoveryPosition position = new RecoveryPosition("subject", 0L, at, false);

    @Test void typedKeysRetainTheSameCanonicalBytesAndRejectNonCanonicalNumbers() {
        for (String value : new String[] {"subject.0", "subject.1", "subject." + Long.MAX_VALUE}) {
            var parsed = RecoveryTaskKey.parse(value);
            assertEquals(value, parsed.bizId());
            assertEquals(parsed, PaymentRecoveryKey.parse(value).taskKey());
            assertEquals(parsed, RefundRecoveryKey.parse(value).taskKey());
        }
        for (String value : Arrays.asList(null, "", "subject", ".0", "subject.", "subject.-1", "subject.+1", "subject.01",
                "subject.1.0x", "subject.9223372036854775808", "x".repeat(129))) {
            assertThrows(DomainViolation.class, () -> RecoveryTaskKey.parse(value));
            assertThrows(DomainViolation.class, () -> PaymentRecoveryKey.parse(value));
            assertThrows(DomainViolation.class, () -> RefundRecoveryKey.parse(value));
        }
    }

    @Test void currentOldCompletedFutureAndDifferentSubjectsUseOneProtocol() {
        assertTrue(position.current(key, at));
        assertFalse(new RecoveryPosition("subject", 1L, at.plusSeconds(30), false).current(key, at));
        assertFalse(new RecoveryPosition("subject", 0L, at, true).current(key, at));
        assertThrows(DomainViolation.class, () -> position.current(new RecoveryTaskKey("subject", 1L), at));
        assertThrows(DomainViolation.class, () -> new RecoveryPosition("subject", 0L, at, true).current(new RecoveryTaskKey("subject", 1L), at));
        assertThrows(DomainViolation.class, () -> position.current(new RecoveryTaskKey("other-subject", 0L), at));
        assertThrows(DomainViolation.class, () -> position.current(key, at.plusSeconds(1)));
    }

    @Test void bothTypedCursorsScheduleTheSameSuccessorAndKeepTheirOriginalSubjects() {
        var expected = position.schedule(at.plusSeconds(30), 0L);
        assertEquals(new RecoveryPosition("subject", 1L, at.plusSeconds(30), false), expected);
        var payment = PaymentRecoveryCursor.init("subject", at).schedule(at.plusSeconds(30), 0L);
        var refund = RefundRecoveryCursor.init("subject", at).schedule(at.plusSeconds(30), 0L);
        assertEquals(expected, payment.position()); assertEquals(expected, refund.position());
        assertFalse(payment.finish().position().current(new RecoveryTaskKey("subject", 1L), at.plusSeconds(30)));
        assertFalse(refund.finish().position().current(new RecoveryTaskKey("subject", 1L), at.plusSeconds(30)));
    }

    @Test void generationOverflowAndNonForwardSchedulingCannotMutateThePosition() {
        assertThrows(DomainViolation.class, () -> position.schedule(at, 0L));
        assertThrows(DomainViolation.class, () -> position.schedule(at.minusSeconds(1), 0L));
        assertThrows(DomainViolation.class, () -> position.schedule(at.plusSeconds(1), 1L));
        assertThrows(DomainViolation.class, () -> new RecoveryPosition("subject", 0L, at, true).schedule(at.plusSeconds(1), 0L));
        var exhausted = new RecoveryPosition("subject", Long.MAX_VALUE, at, false);
        assertThrows(ArithmeticException.class, () -> exhausted.schedule(at.plusSeconds(1), Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, exhausted.generation()); assertEquals(at, exhausted.dueAt());
    }
}
