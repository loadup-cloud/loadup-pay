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

import io.github.loadup.components.oauth.OAuthClientSettings;
import java.util.List;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

final class OAuthTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {
    private final RegisteredClientRepository clients;

    OAuthTokenCustomizer(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        var client = clients.findById(context.getRegisteredClient().getId());
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                || !AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                || client == null
                || !Boolean.TRUE.equals(client.getClientSettings().getSetting(OAuthClientSettings.ENABLED))
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS)
                || !client.getScopes().containsAll(context.getAuthorizedScopes())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        String service = client.getClientSettings().getSetting(PayOAuthClientSettings.SERVICE);
        String audience = client.getClientSettings().getSetting(OAuthClientSettings.AUDIENCE);
        String principalType = client.getClientSettings().getSetting(PayOAuthClientSettings.PRINCIPAL_TYPE);
        String merchantId = client.getClientSettings().getSetting(PayOAuthClientSettings.MERCHANT);
        if (principalType == null || principalType.isBlank()) {
            principalType = PayOAuthClientSettings.SERVICE_TYPE;
        }
        if (service == null || service.isBlank() || audience == null || audience.isBlank()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
        var claims = context.getClaims();
        claims.audience(List.of(audience))
                .claim("principal_type", principalType)
                .claim("token_use", "access")
                .claim(
                        "permissions",
                        context.getAuthorizedScopes().stream().sorted().toList());
        if (PayOAuthClientSettings.SERVICE_TYPE.equals(principalType)) {
            claims.subject(service).claim("service_id", service);
        } else if (PayOAuthClientSettings.MERCHANT_APP.equals(principalType)) {
            if (merchantId == null || merchantId.isBlank()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
            }
            claims.subject(service).claim("service_id", service).claim("merchant_id", merchantId);
        } else if (PayOAuthClientSettings.MERCHANT_TYPE.equals(principalType)) {
            if (merchantId == null || merchantId.isBlank()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
            }
            claims.subject(merchantId).claim("merchant_id", merchantId);
        } else {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }
    }
}
