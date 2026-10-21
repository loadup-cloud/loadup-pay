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
package io.github.loadup.pay.product.domain;

import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyPublicationTest {
    PolicyPublication initial() {
        return new PolicyPublication(
                "publication",
                "contract",
                1,
                "snapshot",
                "merchant",
                "scope",
                "policy",
                1,
                "a".repeat(64),
                "synthetic-private-activation",
                "INIT",
                null,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                null,
                1,
                0);
    }

    @Test
    void anAckCannotActivateAnIntentBeforeItHasADurableEventBinding() {
        assertThrows(
                DomainViolation.class,
                () -> initial().acknowledge("event", initial().requestedAt()));
    }

    @Test
    void wrongEventOrChangedSourceNeverAcknowledgesThePublication() {
        var pending = initial().publishing("event");
        assertThrows(DomainViolation.class, () -> pending.acknowledge("other", pending.requestedAt()));
        assertThrows(DomainViolation.class, () -> pending.requireSource("contract", 1, "changed", "merchant", "scope"));
    }

    @Test
    void duplicateAckKeepsTheOriginalActivationTimeAndVersion() {
        var active =
                initial().publishing("event").acknowledge("event", initial().requestedAt());
        assertSame(active, active.acknowledge("event", active.activatedAt()));
        assertThrows(
                DomainViolation.class,
                () -> active.acknowledge("event", active.activatedAt().plusSeconds(1)));
    }

    @Test
    void diagnosticsDoNotExposeTheFrozenActivationPayload() {
        assertFalse(initial().toString().contains("synthetic-private-activation"));
    }
}
