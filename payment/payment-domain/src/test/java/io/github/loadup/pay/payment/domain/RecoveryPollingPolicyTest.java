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
import io.github.loadup.pay.payment.domain.policy.RecoveryPollingPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryPollingPolicyTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 27, 12, 0);
    private final RecoveryPollingPolicy exact = new RecoveryPollingPolicy(30, 1800, 900, 0);

    @Test
    void ageRaisesTheNominalIntervalUntilItsConfiguredCeiling() {
        assertEquals(30L, seconds(exact, AT, 0L));
        assertEquals(30L, seconds(exact, AT.plusMinutes(14).plusSeconds(59), 0L));
        assertEquals(60L, seconds(exact, AT.plusMinutes(15), 0L));
        assertEquals(120L, seconds(exact, AT.plusMinutes(30), 0L));
        assertEquals(1800L, seconds(exact, AT.plusMinutes(90), 0L));
        assertEquals(1800L, seconds(exact, AT.plusDays(30), 0L));
    }

    @Test
    void restartsAndGenerationsCannotResetAnOldRequestsPollingAge() {
        var now = AT.plusDays(1);
        assertEquals(now.plusMinutes(30), exact.nextAt("tenant", "collection", "payment", 0L, AT, AT, now));
        assertEquals(now.plusMinutes(30), exact.nextAt("tenant", "collection", "payment", 10000L, AT, AT, now));
        assertEquals(30L, seconds(exact, AT, 10000L));
    }

    @Test
    void jitterIsDeterministicPerOriginalIdentityAndGenerationAndStaysWithinBounds() {
        var policy = new RecoveryPollingPolicy(30, 1800, 900, 20);
        var offsets = new HashSet<Long>();
        for (long generation = 0; generation < 64; generation++) {
            var first = policy.nextAt("tenant", "refund", "request", generation, AT, AT, AT);
            assertEquals(first, policy.nextAt("tenant", "refund", "request", generation, AT, AT, AT));
            long delay = Duration.between(AT, first).getSeconds();
            assertTrue(delay >= 30 && delay <= 36);
            offsets.add(delay);
        }
        assertTrue(offsets.size() > 1, "Distinct generations must not all retain the same wake-up offset");
    }

    @Test
    void jitterAtTheCeilingStillSpreadsQueriesWithoutExceedingTheCeiling() {
        var policy = new RecoveryPollingPolicy(30, 1800, 900, 20);
        var now = AT.plusDays(1);
        var offsets = new HashSet<Long>();
        for (long generation = 0; generation < 64; generation++) {
            var at = policy.nextAt("tenant", "collection", "payment", generation, AT, AT, now);
            long delay = Duration.between(now, at).getSeconds();
            assertTrue(delay >= 1440 && delay <= 1800);
            offsets.add(delay);
        }
        assertTrue(offsets.size() > 1);
    }

    @Test
    void aRegressedClockCannotScheduleBeforeTheCurrentDueTime() {
        var due = AT.plusHours(6);
        assertEquals(due.plusMinutes(30), exact.nextAt("tenant", "refund", "request", 9L, AT, due, AT.minusHours(1)));
    }

    @Test
    void veryOldRequestsSaturateInsteadOfOverflowingOrReturningToShortIntervals() {
        var now = AT.plusYears(500);
        assertEquals(now.plusMinutes(30), exact.nextAt("tenant", "collection", "payment", Long.MAX_VALUE, AT, AT, now));
    }

    @Test
    void invalidConfigurationAndInconsistentTimeOrIdentityAreRejected() {
        assertThrows(DomainViolation.class, () -> new RecoveryPollingPolicy(0, 1800, 900, 20));
        assertThrows(DomainViolation.class, () -> new RecoveryPollingPolicy(30, 29, 900, 20));
        assertThrows(DomainViolation.class, () -> new RecoveryPollingPolicy(30, 86401, 900, 20));
        assertThrows(DomainViolation.class, () -> new RecoveryPollingPolicy(30, 1800, 0, 20));
        assertThrows(DomainViolation.class, () -> new RecoveryPollingPolicy(30, 1800, 900, -1));
        assertThrows(DomainViolation.class, () -> new RecoveryPollingPolicy(30, 1800, 900, 51));
        assertThrows(DomainViolation.class, () -> exact.nextAt("tenant", "refund", "request", -1L, AT, AT, AT));
        assertThrows(DomainViolation.class, () -> exact.nextAt("tenant", "refund", "request", 0L, AT, AT.minusSeconds(1), AT));
    }

    private long seconds(RecoveryPollingPolicy policy, LocalDateTime now, long generation) {
        return Duration.between(now, policy.nextAt("tenant", "collection", "payment", generation, AT, AT, now)).getSeconds();
    }
}
