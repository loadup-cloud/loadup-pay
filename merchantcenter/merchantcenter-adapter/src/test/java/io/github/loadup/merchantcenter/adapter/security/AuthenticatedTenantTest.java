/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.merchantcenter.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedTenantTest {
    @Test
    void signedTenantScopesTheWholeOperationAndRestoresOuterContextOnFailure() {
        var jwt = Jwt.withTokenValue("signed")
                .header("alg", "RS256")
                .subject("operator")
                .claim("tenant_id", "tenant_a")
                .build();
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        TenantUtil.callWithTenant("outer", () -> {
            assertThatThrownBy(() -> AuthenticatedTenant.call(authentication, () -> {
                        assertThat(TenantUtil.getTenantId()).isEqualTo("tenant_a");
                        throw new IllegalStateException("synthetic");
                    }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(TenantUtil.getTenantId()).isEqualTo("outer");
            return null;
        });
    }

    @Test
    void missingAuthenticationCannotReachTheOperation() {
        assertThatThrownBy(() -> AuthenticatedTenant.call(null, () -> "unreachable"))
                .isInstanceOf(CommonException.class);
    }
}
