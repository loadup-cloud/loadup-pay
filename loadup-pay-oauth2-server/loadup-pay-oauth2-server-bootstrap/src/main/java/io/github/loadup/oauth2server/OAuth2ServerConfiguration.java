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
package io.github.loadup.oauth2server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class OAuth2ServerConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            Environment environment,
            JsonMapper mapper,
            @Value("${pay.oauth2server.catalog}") String catalogJson) {
        OAuthClientCatalog catalog;
        try {
            catalog = mapper.readValue(catalogJson, OAuthClientCatalog.class);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot load service authorization catalog (details suppressed)");
        }
        return OAuthClientRegistry.synchronize(jdbc, new TransactionTemplate(transactions), catalog, environment);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    OAuthTokenCustomizer oauthTokenCustomizer(RegisteredClientRepository clients) {
        return new OAuthTokenCustomizer(clients);
    }

    @Bean
    @Order(0)
    SecurityFilterChain serviceProtocol(HttpSecurity http) {
        http.oauth2AuthorizationServer(server -> http.securityMatcher(server.getEndpointsMatcher()));
        http.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(10)
    SecurityFilterChain denyOtherEndpoints(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .denyAll());
        return http.build();
    }
}
