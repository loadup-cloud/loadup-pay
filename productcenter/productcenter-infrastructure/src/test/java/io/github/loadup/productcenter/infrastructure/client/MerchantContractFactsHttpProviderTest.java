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
package io.github.loadup.productcenter.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.merchantcenter.client.http.MerchantProfileHttpService;
import io.github.loadup.productcenter.infrastructure.converter.RemoteContractConverter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MerchantContractFactsHttpProviderTest {
    private final MerchantProfileHttpService remote = mock(MerchantProfileHttpService.class);
    private final ServiceAccessTokens tokens = mock(ServiceAccessTokens.class);
    private final RemoteContractConverter contracts = remoteContracts();
    private final MerchantContractFactsHttpProvider provider =
            new MerchantContractFactsHttpProvider(remote, tokens, "merchantcenter", contracts);

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

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void usesDedicatedServiceCredentialAndOnlyReturnsVerifiedFacts() {
        when(tokens.token("productcenter", "merchantcenter")).thenReturn("service-token");
        when(remote.profile("Bearer service-token", new IdQuery("merchant")))
                .thenReturn(SuccessResponse.of(new io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO(
                        "merchant", "M001", "ENTERPRISE", "RETAIL", "CN", null, "", true)));
        var facts = provider.load("merchant");
        assertThat(facts.active()).isTrue();
        assertThat(facts.facts())
                .containsOnlyKeys("merchant.code", "merchant.type", "merchant.industry", "merchant.country");
        verify(remote).profile("Bearer service-token", new IdQuery("merchant"));
    }

    @Test
    void rejectsMismatchedMerchantAndNeverPromotesInactiveMerchant() {
        when(tokens.token("productcenter", "merchantcenter")).thenReturn("service-token");
        when(remote.profile(anyString(), any()))
                .thenReturn(SuccessResponse.of(new io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO(
                        "other", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true)));
        assertThatThrownBy(() -> provider.load("merchant")).isInstanceOf(CommonException.class);
        when(remote.profile(anyString(), any()))
                .thenReturn(SuccessResponse.of(new io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO(
                        "merchant", "M001", "ENTERPRISE", "RETAIL", "CN", null, null, false)));
        assertThat(provider.load("merchant").active()).isFalse();
    }

    @Test
    void neverCallsRemoteInsideTheContractTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> provider.load("merchant")).isInstanceOf(CommonException.class);
        verifyNoInteractions(remote, tokens);
    }
}
