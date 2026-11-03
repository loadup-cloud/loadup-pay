package io.github.loadup.pay.authserver;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.support.TransactionTemplate;

/** Synchronizes a reviewed catalog, then delegates OAuth client persistence to Spring Security. */
final class ServiceClientRegistry {
    static final String PREFIX = "pay.service.";
    static final String TENANT = PREFIX + "tenant";
    static final String SERVICE = PREFIX + "identity";
    static final String AUDIENCE = PREFIX + "audience";
    static final String ENABLED = PREFIX + "enabled";

    static RegisteredClientRepository synchronize(
            JdbcTemplate jdbc, TransactionTemplate transaction, ServiceClientCatalog catalog, Environment environment) {
        var repository = new JdbcRegisteredClientRepository(jdbc);
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        // Resolve/validate all credential sources before the first write, never log the values.
        Map<String, String> secrets = new HashMap<>();
        for (var client : catalog.clients()) {
            if (client.enabled()) {
                String secret = environment.getProperty(client.secretEnv());
                if (secret == null
                        || secret.length() < 32
                        || secret.length() > 72
                        || !secret.matches("[A-Za-z0-9_-]+")) {
                    throw new IllegalArgumentException("Service client secret must be 32-72 URL-safe ASCII characters");
                }
                secrets.put(client.clientId(), secret);
            }
        }
        transaction.executeWithoutResult(status -> {
            // Serialize catalog synchronization across replicas for this dedicated PostgreSQL database.
            jdbc.query("select pg_advisory_xact_lock(?)", (row, index) -> Boolean.TRUE, 0x4c555041555448L);
            var desired = new HashSet<String>();
            for (var client : catalog.clients()) {
                String id = "pay-service-"
                        + UUID.nameUUIDFromBytes(client.clientId().getBytes(StandardCharsets.UTF_8));
                desired.add(id);
                var existing = repository.findByClientId(client.clientId());
                if (existing != null && !id.equals(existing.getId())) {
                    throw new IllegalStateException("Service client id is owned by another registration");
                }
                String secret = client.enabled()
                        ? secrets.get(client.clientId())
                        : UUID.randomUUID().toString();
                String hash = existing != null
                                && existing.getClientSecret() != null
                                && encoder.matches(secret, existing.getClientSecret())
                        ? existing.getClientSecret()
                        : encoder.encode(secret);
                var registration = RegisteredClient.withId(id)
                        .clientId(client.clientId())
                        .clientName(client.clientId())
                        .clientSecret(hash)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scopes(scopes -> scopes.addAll(client.permissions()))
                        .clientSettings(ClientSettings.builder()
                                .setting(TENANT, client.tenantId())
                                .setting(SERVICE, client.serviceId())
                                .setting(AUDIENCE, client.audience())
                                .setting(ENABLED, client.enabled())
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(5))
                                .build())
                        .build();
                repository.save(registration);
            }
            // Removed entries cannot continue obtaining tokens with their previous secret.
            jdbc
                    .queryForList("select id from oauth2_registered_client where id like 'pay-service-%'", String.class)
                    .stream()
                    .filter(id -> !desired.contains(id))
                    .forEach(id -> {
                        var stale = repository.findById(id);
                        if (stale != null) {
                            repository.save(RegisteredClient.from(stale)
                                    .clientSecret(
                                            encoder.encode(UUID.randomUUID().toString()))
                                    .clientSettings(ClientSettings.withSettings(
                                                    stale.getClientSettings().getSettings())
                                            .setting(ENABLED, false)
                                            .build())
                                    .build());
                        }
                    });
        });
        return repository;
    }
}
