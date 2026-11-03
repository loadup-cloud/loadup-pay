package io.github.loadup.pay.authserver;

import java.util.List;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** Claims come exclusively from current server-side grants, never request parameters. */
final class ServiceTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    private final RegisteredClientRepository clients;

    ServiceTokenCustomizer(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        var client = clients.findById(context.getRegisteredClient().getId());
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                || !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                || client == null
                || !Boolean.TRUE.equals(client.getClientSettings().getSetting(ServiceClientRegistry.ENABLED))
                || !client.getScopes().containsAll(context.getAuthorizedScopes())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        String service = client.getClientSettings().getSetting(ServiceClientRegistry.SERVICE);
        String tenant = client.getClientSettings().getSetting(ServiceClientRegistry.TENANT);
        String audience = client.getClientSettings().getSetting(ServiceClientRegistry.AUDIENCE);
        if (service == null || tenant == null || audience == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        context.getClaims()
                .subject(service)
                .audience(List.of(audience))
                .claim("tenant_id", tenant)
                .claim("principal_type", "SERVICE")
                .claim("service_id", service)
                .claim("token_use", "access")
                .claim(
                        "permissions",
                        context.getAuthorizedScopes().stream().sorted().toList());
    }
}
