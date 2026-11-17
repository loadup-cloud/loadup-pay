/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.infrastructure.security;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.loadup.acquire.app.security.AcquireActor;
import io.github.loadup.acquire.app.security.AcquireActorProvider;
import io.github.loadup.commons.error.CommonException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class DelegatedCredentialResolverTest {
    private final AcquireActorProvider actors = mock(AcquireActorProvider.class);
    private final DelegatedCredentialResolver resolver = new DelegatedCredentialResolver(actors);

    @BeforeEach
    void setup() {
        when(actors.current()).thenReturn(new AcquireActor("a".repeat(64), Set.of("pay:acquire:create")));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegatesTheOriginalVerifiedCredentialAfterBusinessActorValidation() {
        authenticate(jwt("synthetic-original-credential"));

        assertEquals("synthetic-original-credential", resolver.resolve());
        verify(actors).current();
    }

    @Test
    void resolvesTheCurrentRequestAfterTokenRotationWithoutCaching() {
        authenticate(jwt("synthetic-first-credential"));
        assertEquals("synthetic-first-credential", resolver.resolve());
        authenticate(jwt("synthetic-next-credential"));
        assertEquals("synthetic-next-credential", resolver.resolve());

        SecurityContextHolder.clearContext();
        assertForbidden();
    }

    @Test
    void absentOrUnauthenticatedContextDoesNotYieldACredential() {
        assertForbidden();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(
                        "unverified", jwt("synthetic-unverified-credential")));
        assertForbidden();
    }

    @Test
    void aRawTokenOrJwtPrincipalCannotReplaceVerifiedJwtCredentials() {
        var jwt = jwt("synthetic-principal-credential");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(jwt, "synthetic-raw-credential", List.of()));
        assertForbidden();
        authenticate(null);
        assertForbidden();
    }

    @Test
    void blankJwtValuesAreRejectedWithoutExposingCredentialDetails() {
        for (String value : Arrays.asList(null, "", " ")) {
            var jwt = mock(Jwt.class);
            when(jwt.getTokenValue()).thenReturn(value);
            authenticate(jwt);
            assertForbidden();
        }
    }

    @Test
    void businessActorRejectionIsNotReclassifiedAsADependencyFailure() {
        authenticate(jwt("synthetic-rejected-credential"));
        var rejection = new CommonException(ACTION_FORBIDDEN);
        when(actors.current()).thenThrow(rejection);

        assertSame(rejection, assertThrows(CommonException.class, resolver::resolve));
    }

    private Jwt jwt(String value) {
        return Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject("app")
                .claim("principal_type", "MERCHANT_APP")
                .claim("merchant_id", "merchant")
                .claim("aud", List.of("acquire"))
                .build();
    }

    private void authenticate(Object credentials) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("verified", credentials, List.of()));
    }

    private void assertForbidden() {
        var failure = assertThrows(CommonException.class, resolver::resolve);
        assertSame(ACTION_FORBIDDEN, failure.getResultCode());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("synthetic-"));
    }
}
