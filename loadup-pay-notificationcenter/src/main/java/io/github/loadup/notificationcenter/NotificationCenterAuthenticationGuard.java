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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Entry guard for notification HTTP surfaces: API routes require a verified JWT.
 *
 * <p>The deployment code header is validated by the shared entry filter; this guard only preserves
 * the original authentication requirement.
 */
@Configuration(proxyBeanMethods = false)
public class NotificationCenterAuthenticationGuard {
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> notificationAuthenticationGuard() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                String path = request.getServletPath();
                if (path.startsWith("/api/")) {
                    var authentication = SecurityContextHolder.getContext().getAuthentication();
                    if (authentication == null
                            || !authentication.isAuthenticated()
                            || !(authentication.getCredentials() instanceof Jwt)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        return;
                    }
                }
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setName("notificationAuthenticationGuard");
        // Spring Security runs first and establishes the verified token.
        registration.setOrder(0);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
