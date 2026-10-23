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
package io.github.loadup.pay.acquire.infrastructure.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionState;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.infrastructure.client.CheckoutPresentationImpl;
import io.github.loadup.pay.acquire.infrastructure.config.CheckoutProperties;
import io.github.loadup.commons.time.BusinessTime;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacCheckoutTokensTest {
    final String oldSecret = "synthetic-v1-" + "a".repeat(32), newSecret = "synthetic-v2-" + "b".repeat(32);
    final LocalDateTime expiry = LocalDateTime.of(2026, 10, 21, 12, 5);
    final CheckoutSession session = new CheckoutSession("session", "order", CheckoutSessionState.ACTIVE, expiry);
    final String nonce = "c".repeat(64);

    CheckoutProperties properties(String active, Map<String, String> keys) {
        return new CheckoutProperties(URI.create("https://pay.example/checkout"), "checkout", active, keys);
    }

    HmacCheckoutTokens issuer(String active, Map<String, String> keys) {
        return new HmacCheckoutTokens(properties(active, keys));
    }

    StoredCheckoutSession stored(HmacCheckoutTokens tokens, String token) {
        return new StoredCheckoutSession(
                session,
                "merchant",
                "request",
                "a".repeat(64),
                tokens.hash(token),
                nonce,
                "checkout",
                "v1",
                "https://pay.example",
                new CheckoutSessionScope(1, "b".repeat(64), "return-v1", Set.of("ORDER_SUMMARY")),
                0);
    }

    @Test
    void protocolMatchesIndependentJcaHmacWithExactLfAndEpochFields() throws Exception {
        var tokens = issuer("v1", Map.of("v1", oldSecret));
        String data = "pay-checkout-v1\nsession\norder\n"
                + BusinessTime.external(expiry).toEpochSecond() + "\n" + nonce + "\n";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(oldSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "session."
                + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, tokens.issue(session, nonce, "checkout", "v1"));
        assertTrue(tokens.matches(expected, stored(tokens, expected)));
    }

    @Test
    void rotationReplaysOldVersionAndNewSessionsUseTheNewVersion() {
        var old = issuer("v1", Map.of("v1", oldSecret));
        String token = old.issue(session, nonce, "checkout", "v1");
        var rotated = issuer("v2", Map.of("v1", oldSecret, "v2", newSecret));
        assertEquals(token, rotated.issue(session, nonce, "checkout", "v1"));
        assertNotEquals(token, rotated.issue(session, nonce, "checkout", "v2"));
        assertTrue(rotated.matches(token, stored(old, token)));
    }

    @Test
    void removedKeyPreventsReissueButDoesNotFabricateAReplacementCredential() {
        var old = issuer("v1", Map.of("v1", oldSecret));
        String token = old.issue(session, nonce, "checkout", "v1");
        var removed = issuer("v2", Map.of("v2", newSecret));
        assertThrows(CommonException.class, () -> removed.issue(session, nonce, "checkout", "v1"));
        assertTrue(removed.matches(token, stored(old, token))); // Read authentication uses the original stored digest.
    }

    @Test
    void nonceAndExpiryAreBoundAndInvalidOrChangedTokensFailDigestComparison() {
        var tokens = issuer("v1", Map.of("v1", oldSecret));
        String token = tokens.issue(session, nonce, "checkout", "v1");
        var stored = stored(tokens, token);
        assertFalse(tokens.matches(token.substring(0, token.length() - 1) + "!", stored));
        assertFalse(tokens.matches("other." + "A".repeat(43), stored));
        assertFalse(tokens.matches(tokens.issue(session, "d".repeat(64), "checkout", "v1"), stored));
        assertFalse(tokens.matches(
                tokens.issue(
                        new CheckoutSession("session", "order", CheckoutSessionState.ACTIVE, expiry.plusSeconds(1)),
                        nonce,
                        "checkout",
                        "v1"),
                stored));
    }

    @Test
    void newNonceIs32RandomBytesAndDiagnosticOutputContainsNoKeysOrTokenDigest() {
        var props = properties("v1", Map.of("v1", oldSecret));
        var tokens = new HmacCheckoutTokens(props);
        assertTrue(tokens.nonce().matches("[0-9a-f]{64}"));
        assertNotEquals(tokens.nonce(), tokens.nonce());
        assertFalse(props.toString().contains(oldSecret));
        String token = tokens.issue(session, nonce, "checkout", "v1");
        var stored = stored(tokens, token);
        assertFalse(stored.toString().contains(token));
        assertFalse(stored.toString().contains(tokens.hash(token)));
    }

    @Test
    void pageOriginIsServerControlledAndCapabilitiesRemainOnlyInTheFragment() {
        var presentation = new CheckoutPresentationImpl(properties("v1", Map.of("v1", oldSecret)));
        var url = URI.create(presentation.url("tenant", "order", "synthetic-capability"));
        assertNull(url.getQuery());
        assertTrue(url.getFragment().contains("synthetic-capability"));
        assertEquals("https://pay.example", presentation.allowedOrigin());
        var canonical = new CheckoutPresentationImpl(new CheckoutProperties(
                URI.create("https://PAY.example:443/checkout"), "checkout", "v1", Map.of("v1", oldSecret)));
        assertEquals("https://pay.example", canonical.allowedOrigin());
    }

    @Test
    void unsafePageTargetsAndShortSecretsFailAtStartup() {
        for (String page : List.of(
                "http://public.example/checkout",
                "https://user:pass@pay.example/checkout",
                "https://pay.example/checkout?return=bad",
                "https://pay.example/checkout#bad"))
            assertThrows(
                    IllegalStateException.class,
                    () -> new CheckoutProperties(URI.create(page), "checkout", "v1", Map.of("v1", oldSecret)));
        assertThrows(IllegalStateException.class, () -> properties("v1", Map.of("v1", "short")));
    }
}
