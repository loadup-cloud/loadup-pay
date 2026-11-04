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
package io.github.loadup.merchantmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.http.MerchantManagementHttpService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ManagementAuthorizationTest {
    private final MerchantManagementHttpService remote = mock(MerchantManagementHttpService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MethodSecurity.class)
            .withBean(MerchantManagementHttpService.class, () -> remote)
            .withBean(ManagementResponses.class)
            .withBean(MerchantManagementController.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void forwardsOriginalOperatorBearerWithoutReplacingIdentity() {
        authenticate("OPERATOR", "merchant:read");
        var query = new IdQuery("resource");
        var data = mock(MerchantDTO.class);
        when(remote.detail("Bearer original", query)).thenReturn(SuccessResponse.of(data));
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MerchantManagementController.class)
                            .detail("Bearer original", query)
                            .data())
                    .isSameAs(data);
        });
        verify(remote).detail("Bearer original", query);
    }

    @Test
    void rejectsServiceIdentityAndMissingPermissionBeforeCallingCenter() {
        runner.run(context -> {
            var controller = context.getBean(MerchantManagementController.class);
            authenticate("SERVICE", "merchant:read");
            assertThatThrownBy(() -> controller.detail("Bearer service", new IdQuery("resource")))
                    .isInstanceOf(AccessDeniedException.class);
            authenticate("OPERATOR", "unrelated");
            assertThatThrownBy(() -> controller.detail("Bearer operator", new IdQuery("resource")))
                    .isInstanceOf(AccessDeniedException.class);
        });
        verifyNoInteractions(remote);
    }

    private void authenticate(String type, String permission) {
        var jwt = Jwt.withTokenValue("original")
                .header("alg", "RS256")
                .subject("operator")
                .claim("principal_type", type)
                .claim("tenant_id", "tenant_a")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(permission))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurity {}
}
