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
package io.github.loadup.notificationcenter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

class NotificationCenterTenantFilterTest {
    private static final String CONFIGURED_TENANT = "tenant-a";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permitsConfiguredTenantOnApiAndNotificationPaths() throws Exception {
        var filter = filter();

        assertAllowed(filter, "/api/internal/status", authenticated("tenant-a"));
        assertAllowed(filter, "/notifications/list", authenticated("tenant-a"));
    }

    @Test
    void ignoresJwtTenantClaimsAndRejectsUnauthenticatedRequests() throws Exception {
        var filter = filter();

        assertAllowed(filter, "/api/internal/status", authenticated("tenant-b"));
        assertAllowed(filter, "/notifications/list", authenticated(null));
        assertForbidden(filter, "/api/internal/status", null);
    }

    @Test
    void leavesHealthPathOutsideTenantGuard() throws Exception {
        assertAllowed(filter(), "/actuator/health", null);
    }

    private OncePerRequestFilter filter() {
        var environment =
                new MockEnvironment().withProperty("loadup.database.multi-tenant.default-tenant-id", CONFIGURED_TENANT);
        FilterRegistrationBean<OncePerRequestFilter> registration =
                new NotificationCenterTenantFilter().notificationTenantGuard(environment);
        return registration.getFilter();
    }

    private void assertAllowed(
            OncePerRequestFilter filter, String path, UsernamePasswordAuthenticationToken authentication)
            throws Exception {
        setAuthentication(authentication);
        var request = request(path);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isNotEqualTo(403);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private void assertForbidden(
            OncePerRequestFilter filter, String path, UsernamePasswordAuthenticationToken authentication)
            throws Exception {
        setAuthentication(authentication);
        var request = request(path);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }

    private UsernamePasswordAuthenticationToken authenticated(String tenant) {
        Jwt.Builder builder = Jwt.withTokenValue("verified-token")
                .header("alg", "RS256")
                .subject("notification-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenant != null) {
            builder.claim("tenant_id", tenant);
        }
        return UsernamePasswordAuthenticationToken.authenticated("notification-client", builder.build(), List.of());
    }

    private void setAuthentication(UsernamePasswordAuthenticationToken authentication) {
        SecurityContextHolder.clearContext();
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
