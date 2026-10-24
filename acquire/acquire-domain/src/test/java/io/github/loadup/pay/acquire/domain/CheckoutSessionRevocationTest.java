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
package io.github.loadup.pay.acquire.domain;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.acquire.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutSessionRevocationTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 24, 12, 0);

    @Test
    void revocationPreservesEveryCredentialAndBusinessStateIncludingTerminalSessions() {
        for (var state : CheckoutSessionState.values()) {
            var before = stored(state);
            var after = before.revoke(0L, AT);
            assertEquals(before.session(), after.session());
            assertEquals(before.scope(), after.scope());
            assertEquals(before.tokenHash(), after.tokenHash());
            assertEquals(before.tokenNonce(), after.tokenNonce());
            assertEquals(before.tokenKeyRef(), after.tokenKeyRef());
            assertEquals(before.tokenKeyVersion(), after.tokenKeyVersion());
            assertEquals(before.requestDigest(), after.requestDigest());
            assertEquals(before.requestKey(), after.requestKey());
            assertEquals(before.allowedOrigin(), after.allowedOrigin());
            assertEquals(before.payeeMerchantId(), after.payeeMerchantId());
            assertNull(before.revokedAt());
            assertEquals(AT, after.revokedAt());
            assertEquals(1L, after.rowVersion());
            assertSame(after, after.revoke(1L, AT.plusDays(1)));
        }
    }

    @Test
    void staleVersionsCannotRevokeOrChangeTheOriginalMarker() {
        var before = stored(CheckoutSessionState.ACTIVE);
        assertThrows(DomainViolation.class, () -> before.revoke(1L, AT));
        var revoked = before.revoke(0L, AT);
        assertThrows(DomainViolation.class, () -> revoked.revoke(0L, AT.plusSeconds(1)));
        assertEquals(AT, revoked.revokedAt());
    }

    @Test
    void receiptsRequireTheExactVersionTransitionAndHideTheReasonInDiagnostics() {
        var receipt = new CheckoutSessionRevocationReceipt("receipt", "session", "order", "request",
                "a".repeat(64), "b".repeat(64), "private-revocation-reason", true, 0L, 1L, AT, AT);
        assertFalse(receipt.toString().contains("private-revocation-reason"));
        assertThrows(DomainViolation.class, () -> new CheckoutSessionRevocationReceipt("receipt", "session", "order", "request",
                "a".repeat(64), "b".repeat(64), "reason", true, 0L, 2L, AT, AT));
        assertThrows(DomainViolation.class, () -> new CheckoutSessionRevocationReceipt("receipt", "session", "order", "request",
                "a".repeat(64), "b".repeat(64), "reason", false, 0L, 1L, AT, AT));
    }

    private static StoredCheckoutSession stored(CheckoutSessionState state) {
        return new StoredCheckoutSession(new CheckoutSession("session", "order", state, AT.plusMinutes(5)), "merchant",
                "issue", "a".repeat(64), "b".repeat(64), "c".repeat(64), "key", "v1", "https://pay.example",
                new CheckoutSessionScope(1, "d".repeat(64), "return", Set.of("ORDER_SUMMARY", "PAYMENT_START", "PAYMENT_PROGRESS")),
                null, 0L);
    }
}
