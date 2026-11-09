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
package io.github.loadup.productcenter.infrastructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO;
import io.github.loadup.productcenter.infrastructure.config.PolicyPublicationTransportProperties;
import io.github.loadup.productcenter.infrastructure.converter.RemoteContractConverter;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LimitPolicyActivationHttpClientTest {
    final LimitCenterHttpService remote = mock(LimitCenterHttpService.class);
    final ServiceAccessTokens tokens = mock(ServiceAccessTokens.class);
    final RemoteContractConverter contracts = remoteContracts();
    final LimitPolicyActivationHttpClient client = new LimitPolicyActivationHttpClient(
            remote, new PolicyPublicationTransportProperties("product", "limitcenter"), tokens, contracts);

    private static RemoteContractConverter remoteContracts() {
        var reference = new AtomicReference<RemoteContractConverter>();
        new ApplicationContextRunner()
                .withInitializer(context -> ((BeanDefinitionRegistry) context.getBeanFactory())
                        .registerBeanDefinition(
                                "remoteContractConverter", new RootBeanDefinition(remoteContractConverterType())))
                .run(context -> reference.set(context.getBean(RemoteContractConverter.class)));
        return reference.get();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends RemoteContractConverter> remoteContractConverterType() {
        try {
            return (Class<? extends RemoteContractConverter>)
                    Class.forName(RemoteContractConverter.class.getName() + "Impl");
        } catch (ClassNotFoundException missingGeneratedMapper) {
            throw new IllegalStateException(
                    "Generated RemoteContractConverter implementation is unavailable", missingGeneratedMapper);
        }
    }

    @Test
    void eachAttemptUsesTheSharedCredentialSource() {
        when(tokens.token("product", "limitcenter")).thenReturn("first", "second");
        var ack = new LimitPolicyActivationDTO("policy", 1, "a".repeat(64), OffsetDateTime.now());
        when(remote.activate(anyString(), isNull()))
                .thenReturn(SuccessResponse.of(new io.github.loadup.limitcenter.client.dto.LimitPolicyActivationDTO(
                        "policy", 1, "a".repeat(64), ack.activatedAt())));
        assertEquals(ack, TenantUtil.callWithTenant("tenant", () -> client.activate(null)));
        assertEquals(ack, TenantUtil.callWithTenant("tenant", () -> client.activate(null)));
        verify(remote).activate("Bearer first", null);
        verify(remote).activate("Bearer second", null);
    }

    @Test
    void anotherTenantIsRejectedBeforeAuthorizationOrTransport() {
        assertThrows(CommonException.class, () -> client.activate(null));
        verifyNoInteractions(tokens, remote);
    }

    @Test
    void authorizationFailureDoesNotReachTheBusinessProviderOrLeakCredentials() {
        when(tokens.token(anyString(), anyString())).thenThrow(new IllegalStateException("synthetic-secret"));
        var failure = assertThrows(
                CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> client.activate(null)));
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("synthetic-secret"));
        verifyNoInteractions(remote);
    }

    @Test
    void remoteBusinessFailureIsNotAnActivationAck() {
        when(tokens.token(anyString(), anyString())).thenReturn("token");
        when(remote.activate(anyString(), isNull()))
                .thenReturn(
                        new SuccessResponse<>(Result.buildFailure(LimitCenterResultCodeEnum.POLICY_CONFLICT), null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> client.activate(null)));
    }
}
