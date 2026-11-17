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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.oauth.OAuthClientSettings;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

class PayOAuthClientPolicyTest {
    private final PayOAuthClientPolicy policy = new PayOAuthClientPolicy();

    private static RegisteredClient client(String type, String merchant) {
        var settings = ClientSettings.builder()
                .setting(OAuthClientSettings.AUDIENCE, "payment")
                .setting(PayOAuthClientSettings.SERVICE, "acquire")
                .setting(PayOAuthClientSettings.PRINCIPAL_TYPE, type);
        if (merchant != null) {
            settings.setting(PayOAuthClientSettings.MERCHANT, merchant);
        }
        return RegisteredClient.withId("id")
                .clientId("application")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("pay:payment:read")
                .clientSettings(settings.build())
                .build();
    }

    @Test
    void acceptsOnlyPayIdentityTuplesWithoutRequiringTenantMetadata() {
        assertThatCode(() -> policy.validate(client("SERVICE", null))).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(client("MERCHANT_APP", "merchant")))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(client("MERCHANT", "merchant"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(client("MERCHANT", null))).isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> policy.validate(client("SERVICE", "merchant"))).isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> policy.validate(client("OPERATOR", null))).isInstanceOf(CommonException.class);
    }

    @Test
    void rejectsUserGrantsNonPayScopesAndInvalidBusinessMetadata() {
        var service = client("SERVICE", null);
        var userGrant = RegisteredClient.from(service)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.test/callback")
                .build();
        assertThatThrownBy(() -> policy.validate(userGrant)).isInstanceOf(CommonException.class);
        var scope = RegisteredClient.from(service).scope("unrelated:admin").build();
        assertThatThrownBy(() -> policy.validate(scope)).isInstanceOf(CommonException.class);
        var invalidAudience = RegisteredClient.from(service)
                .clientSettings(
                        ClientSettings.withSettings(service.getClientSettings().getSettings())
                                .setting(OAuthClientSettings.AUDIENCE, " forged ")
                                .build())
                .build();
        assertThatThrownBy(() -> policy.validate(invalidAudience)).isInstanceOf(CommonException.class);
    }
}
