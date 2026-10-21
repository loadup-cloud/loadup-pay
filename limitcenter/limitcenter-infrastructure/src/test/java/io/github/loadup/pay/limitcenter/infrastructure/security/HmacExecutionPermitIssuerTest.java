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
package io.github.loadup.pay.limitcenter.infrastructure.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.infrastructure.config.LimitPermitProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacExecutionPermitIssuerTest {
    final String oldSecret = "test-only-random-material-0123456789abcdef";
    final String newSecret = "test-only-rotated-material-fedcba9876543210";

    StoredLimitReservation stored(String caller, long amount) {
        var now = LocalDateTime.of(2026, 10, 21, 10, 0);
        var value = LimitReservation.init(
                        "reservation",
                        "operation",
                        "payment",
                        "order",
                        "merchant",
                        DomainChecks.amount(amount, "CNY"),
                        "policy",
                        1,
                        now.minusHours(1),
                        now.plusSeconds(120))
                .reserve()
                .beginExecution("attempt", now);
        return new StoredLimitReservation(value, "a".repeat(64), "b".repeat(64), caller);
    }

    HmacExecutionPermitIssuer issuer(String active) {
        return new HmacExecutionPermitIssuer(
                new LimitPermitProperties(active, Map.of("old", oldSecret, "new", newSecret)));
    }

    String issue(
            HmacExecutionPermitIssuer issuer, String tenant, StoredLimitReservation stored, String key, long epoch) {
        return issuer.issue(tenant, stored, "attempt", epoch, "request", key);
    }

    @Test
    void replayIsDeterministicAndRotationDoesNotChangeOldIssuances() {
        var original = issue(issuer("old"), "tenant", stored("payment-service", 40), "old", 1);
        assertTrue(original.matches("[0-9a-f]{64}"));
        assertEquals(original, issue(issuer("new"), "tenant", stored("payment-service", 40), "old", 1));
    }

    @Test
    void tenantCallerAmountAndEpochAreCryptographicallyBound() {
        var issuer = issuer("old");
        var stored = stored("payment-service", 40);
        var original = issue(issuer, "tenant", stored, "old", 1);
        assertNotEquals(original, issue(issuer, "other-tenant", stored, "old", 1));
        assertNotEquals(original, issue(issuer, "tenant", stored("another-service", 40), "old", 1));
        assertNotEquals(original, issue(issuer, "tenant", stored("payment-service", 41), "old", 1));
        assertNotEquals(original, issue(issuer, "tenant", stored, "old", 2));
    }

    @Test
    void missingHistoricalKeyFailsClosed() {
        assertThrows(
                CommonException.class,
                () -> issue(issuer("new"), "tenant", stored("payment-service", 40), "removed", 1));
    }

    @Test
    void diagnosticStringsHideSecretsAndIncomingPermit() {
        var properties = new LimitPermitProperties("old", Map.of("old", oldSecret));
        assertFalse(properties.toString().contains(oldSecret));
        var input = new io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand(
                "operation", "payment", "attempt", 1, 40, "CNY", "d".repeat(64));
        assertFalse(input.toString().contains("d".repeat(64)));
    }

    @Test
    void aSigningKeyIsRequiredAndCannotBeAShortDevelopmentDefault() {
        assertThrows(IllegalStateException.class, () -> new LimitPermitProperties("old", Map.of("old", "short")));
        assertThrows(IllegalStateException.class, () -> new LimitPermitProperties("missing", Map.of("old", oldSecret)));
    }
}
