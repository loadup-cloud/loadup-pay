package io.github.loadup.pay.authserver;

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
class AuthServerConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactions,
            Environment environment,
            @Value("${pay.authserver.catalog}") String catalogJson) {
        ServiceClientCatalog catalog;
        try {
            catalog = JsonMapper.builder().build().readValue(catalogJson, ServiceClientCatalog.class);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot load service authorization catalog (details suppressed)");
        }
        return ServiceClientRegistry.synchronize(jdbc, new TransactionTemplate(transactions), catalog, environment);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    ServiceTokenCustomizer serviceTokenCustomizer(RegisteredClientRepository clients) {
        return new ServiceTokenCustomizer(clients);
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
