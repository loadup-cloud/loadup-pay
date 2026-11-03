package io.github.loadup.pay.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/** Uses the service manager, including on scheduler threads without a servlet request. */
@AutoConfiguration
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class ServiceAuthAutoConfiguration {
    private record Route(String tenant, String service, String audience) {}

    @Bean
    @ConditionalOnMissingBean(ServiceAccessTokens.class)
    ServiceAccessTokens serviceAccessTokens(ServiceAuthProperties properties) {
        return create(properties, Clock.systemUTC());
    }

    static ServiceAccessTokens create(ServiceAuthProperties properties, Clock clock) {
        URI endpoint = URI.create(properties.getTokenUri());
        if (endpoint.getHost() == null
                || endpoint.getUserInfo() != null
                || endpoint.getFragment() != null
                || endpoint.getQuery() != null
                || !("https".equals(endpoint.getScheme())
                        || properties.isAllowInsecureHttp() && "http".equals(endpoint.getScheme()))) {
            throw new IllegalArgumentException("Service token endpoint requires HTTPS (HTTP requires explicit opt-in)");
        }
        if (properties.getClientSecret() == null
                || properties.getClientSecret().isBlank()
                || properties.getRegistrations().isEmpty()) {
            throw new IllegalArgumentException("Service credentials and registrations are required");
        }
        Map<Route, String> routes = new HashMap<>();
        var clients = new ArrayList<ClientRegistration>();
        properties.getRegistrations().forEach((id, registration) -> {
            requireText(registration.clientId());
            requireText(registration.tenantId());
            requireText(registration.serviceId());
            requireText(registration.audience());
            var route = new Route(registration.tenantId(), registration.serviceId(), registration.audience());
            if (routes.putIfAbsent(route, id) != null) {
                throw new IllegalArgumentException("Duplicate service authorization route");
            }
            clients.add(ClientRegistration.withRegistrationId(id)
                    .clientId(registration.clientId())
                    .clientSecret(properties.getClientSecret())
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .tokenUri(endpoint.toString())
                    .scope(registration.scopes())
                    .build());
        });
        var registrations = new InMemoryClientRegistrationRepository(clients);
        var cache = new InMemoryOAuth2AuthorizedClientService(registrations);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, cache);
        var responseClient = new RestClientClientCredentialsTokenResponseClient();
        positiveTimeout(properties.getConnectTimeout());
        positiveTimeout(properties.getReadTimeout());
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        factory.setReadTimeout(properties.getReadTimeout());
        responseClient.setRestClient(RestClient.builder()
                .requestFactory(factory)
                .configureMessageConverters(converters -> converters
                        .disableDefaults()
                        .addCustomConverter(new FormHttpMessageConverter())
                        .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
        provider.setClock(clock);
        provider.setClockSkew(Duration.ofSeconds(30));
        provider.setAccessTokenResponseClient(responseClient);
        manager.setAuthorizedClientProvider(provider);
        Map<String, Object> locks = new HashMap<>();
        routes.values().forEach(id -> locks.put(id, new Object()));
        return (tenant, service, audience) -> {
            String id = routes.get(new Route(tenant, service, audience));
            if (id == null) {
                throw new IllegalStateException("Service authorization route is not registered");
            }
            // One refresh per route; identities never come from the current web principal.
            synchronized (locks.get(id)) {
                try {
                    var client = manager.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(id)
                            .principal("loadup-service:" + id)
                            .build());
                    if (client == null) {
                        throw new IllegalStateException("Missing service access token");
                    }
                    return client.getAccessToken().getTokenValue();
                } catch (RuntimeException failure) {
                    // Never include OAuth response bodies, credentials or tokens in diagnostics.
                    throw new IllegalStateException("Service authorization unavailable");
                }
            }
        };
    }

    private static void requireText(String value) {
        if (value == null
                || value.isBlank()
                || !value.equals(value.trim())
                || value.length() > 128
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid service authorization route");
        }
    }

    private static void positiveTimeout(Duration timeout) {
        if (timeout == null
                || timeout.isNegative()
                || timeout.isZero()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Service token timeout must be positive and at most 30 seconds");
        }
    }
}
