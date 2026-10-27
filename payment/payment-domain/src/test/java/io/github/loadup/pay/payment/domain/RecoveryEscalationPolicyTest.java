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
import io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static io.github.loadup.pay.payment.domain.policy.RecoveryEscalationPolicy.Level.*;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryEscalationPolicyTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    final RecoveryEscalationPolicy policy = new RecoveryEscalationPolicy(900L, 86400L);
    @Test void exactThresholdsEscalateWithoutRoundingUpPartialSeconds() {
        assertEquals(NORMAL, policy.level(at, at.plusSeconds(900).minusNanos(1000)));
        assertEquals(REVIEW, policy.level(at, at.plusSeconds(900)));
        assertEquals(REVIEW, policy.level(at, at.plusSeconds(86400).minusNanos(1000)));
        assertEquals(URGENT, policy.level(at, at.plusSeconds(86400)));
        assertEquals(URGENT, policy.level(at, at.plusYears(20)));
    }
    @Test void cutoffsUseTheSameInclusiveBoundariesAndClockRegressionCannotCreateNegativeAge() {
        assertNull(policy.cutoff(NORMAL, at));
        assertEquals(at.minusSeconds(900), policy.cutoff(REVIEW, at));
        assertEquals(at.minusSeconds(86400), policy.cutoff(URGENT, at));
        assertEquals(0L, policy.ageSeconds(at, at.minusHours(1)));
        assertEquals(NORMAL, policy.level(at, at.minusHours(1)));
    }
    @Test void invalidOrUnorderedConfigurationCannotDisableEscalationSilently() {
        assertThrows(DomainViolation.class, () -> new RecoveryEscalationPolicy(0, 86400));
        assertThrows(DomainViolation.class, () -> new RecoveryEscalationPolicy(900, 900));
        assertThrows(DomainViolation.class, () -> new RecoveryEscalationPolicy(900, 899));
        assertThrows(DomainViolation.class, () -> new RecoveryEscalationPolicy(1, 31536001L));
    }
}
