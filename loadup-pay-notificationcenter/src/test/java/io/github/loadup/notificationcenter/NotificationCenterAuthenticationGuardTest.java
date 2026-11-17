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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

class NotificationCenterAuthenticationGuardTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permitsVerifiedTokensOnApiAndNotificationPaths() throws Exception {
        var filter = filter();
        assertAllowed(filter, "/api/internal/status", authenticated());
        assertAllowed(filter, "/notifications/list", authenticated());
    }

    @Test
    void rejectsUnauthenticatedApiRequests() throws Exception {
        var filter = filter();
        assertForbidden(filter, "/api/internal/status", null);
        assertForbidden(filter, "/notifications/list", null);
    }

    @Test
    void leavesHealthPathOutsideTheGuard() throws Exception {
        assertAllowed(filter(), "/actuator/health", null);
    }

    private OncePerRequestFilter filter() {
        FilterRegistrationBean<OncePerRequestFilter> registration =
                new NotificationCenterAuthenticationGuard().notificationAuthenticationGuard();
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

    private UsernamePasswordAuthenticationToken authenticated() {
        Jwt jwt = Jwt.withTokenValue("verified-token")
                .header("alg", "RS256")
                .subject("notification-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return UsernamePasswordAuthenticationToken.authenticated("notification-client", jwt, List.of());
    }

    private void setAuthentication(UsernamePasswordAuthenticationToken authentication) {
        SecurityContextHolder.clearContext();
        if (authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }
}
