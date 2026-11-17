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

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.oauth.OAuthClientSettings;
import io.github.loadup.components.oauth.client.OAuthClientPolicy;
import java.util.Set;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/** Pay service-only grants and the identity tuple used by Pay resource servers. */
final class PayOAuthClientPolicy implements OAuthClientPolicy {
    private static final Set<String> ALLOWED_SETTINGS = Set.of(
            "settings.client.require-proof-key",
            "settings.client.require-authorization-consent",
            OAuthClientSettings.ENABLED,
            OAuthClientSettings.AUDIENCE,
            "loadup.oauth.revision",
            PayOAuthClientSettings.SERVICE,
            PayOAuthClientSettings.PRINCIPAL_TYPE,
            PayOAuthClientSettings.MERCHANT);

    @Override
    public void validate(RegisteredClient client) {
        if (!ALLOWED_SETTINGS.containsAll(
                client.getClientSettings().getSettings().keySet())) {
            throw invalid();
        }
        if (!client.getAuthorizationGrantTypes().equals(Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS))
                || !client.getClientAuthenticationMethods()
                        .equals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC))
                || client.getScopes().isEmpty()
                || client.getScopes().stream()
                        .anyMatch(scope -> !scope.matches("(?:internal|pay:[a-z0-9:-]{1,120})"))) {
            throw invalid();
        }
        requireId(setting(client, OAuthClientSettings.AUDIENCE), 128);
        requireId(setting(client, PayOAuthClientSettings.SERVICE), 64);
        String type = setting(client, PayOAuthClientSettings.PRINCIPAL_TYPE);
        if (type == null) {
            type = PayOAuthClientSettings.SERVICE_TYPE;
        }
        if (!Set.of(
                        PayOAuthClientSettings.SERVICE_TYPE,
                        PayOAuthClientSettings.MERCHANT_APP,
                        PayOAuthClientSettings.MERCHANT_TYPE)
                .contains(type)) {
            throw invalid();
        }
        String merchant = setting(client, PayOAuthClientSettings.MERCHANT);
        if (!PayOAuthClientSettings.SERVICE_TYPE.equals(type)) {
            requireId(merchant, 64);
        } else if (merchant != null) {
            throw invalid();
        }
    }

    private static String setting(RegisteredClient client, String key) {
        Object value = client.getClientSettings().getSetting(key);
        if (value != null && !(value instanceof String)) {
            throw invalid();
        }
        return (String) value;
    }

    private static void requireId(String value, int maximum) {
        if (value == null || value.length() > maximum || !value.matches("[A-Za-z0-9_.-]+")) {
            throw invalid();
        }
    }

    private static CommonException invalid() {
        return new CommonException(CommonResultCodeEnum.PARAM_ILLEGAL);
    }
}
