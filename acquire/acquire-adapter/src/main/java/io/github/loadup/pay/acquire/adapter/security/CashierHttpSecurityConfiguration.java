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
package io.github.loadup.pay.acquire.adapter.security;

import io.github.loadup.commons.result.FailureResponse;
import io.github.loadup.commons.json.JsonUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class CashierHttpSecurityConfiguration {
    @Bean
    @Order(90)
    SecurityFilterChain cashierSecurityFilterChain(HttpSecurity http, ObjectProvider<JsonMapper> mappers)
            throws Exception {
        JsonMapper mapper = mappers.getIfAvailable(
                () -> JsonUtil.customize(JsonMapper.builder()).build());
        // Opaque capabilities are checked by the request-backed CashierActorProvider on every Facade invocation.
        // They must not enter the merchant JWT decoder. All other cashier endpoints remain denied.
        http.securityMatcher("/api/cashier/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/cashier/order/summary")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                            response.getOutputStream()
                                    .write(mapper.writeValueAsBytes(FailureResponse.of(SESSION_UNAUTHORIZED)));
                        })
                        .accessDeniedHandler((request, response, error) -> {
                            response.setStatus(200);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                            response.getOutputStream()
                                    .write(mapper.writeValueAsBytes(FailureResponse.of(SESSION_UNAUTHORIZED)));
                        }));
        return http.build();
    }
}
