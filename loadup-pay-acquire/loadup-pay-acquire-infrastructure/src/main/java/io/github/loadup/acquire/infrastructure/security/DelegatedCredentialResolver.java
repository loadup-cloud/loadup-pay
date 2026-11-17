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

import io.github.loadup.acquire.app.security.AcquireActorProvider;
import io.github.loadup.commons.error.CommonException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves the original verified caller credential for the current synchronous delegation only. */
@Component
public class DelegatedCredentialResolver {
    private final AcquireActorProvider actors;

    public DelegatedCredentialResolver(AcquireActorProvider actors) {
        this.actors = actors;
    }

    public String resolve() {
        // Preserve the existing actor validation before accessing its transport credential.
        actors.current();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getCredentials() instanceof Jwt jwt)
                || jwt.getTokenValue() == null
                || jwt.getTokenValue().isBlank()) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        // Inbound Resource Server and the target Resource Server retain their own JWT validation.
        return jwt.getTokenValue();
    }
}
