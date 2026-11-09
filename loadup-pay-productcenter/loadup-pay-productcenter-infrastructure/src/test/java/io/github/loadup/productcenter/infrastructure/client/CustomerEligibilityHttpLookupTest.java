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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.merchantcenter.client.error.CustomerResultCodeEnum;
import io.github.loadup.merchantcenter.client.http.CustomerHttpService;
import io.github.loadup.productcenter.app.security.ProductActor;
import io.github.loadup.productcenter.client.error.ProductResultCodeEnum;
import io.github.loadup.productcenter.infrastructure.converter.RemoteContractConverter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CustomerEligibilityHttpLookupTest {
    private static final String TOKEN = "synthetic-customer-token";
    private final CustomerHttpService http = mock(CustomerHttpService.class);
    private final ProductActor actor = new ProductActor("tenant", "MERCHANT_APP", "app", "merchant", TOKEN, Set.of());
    private final CustomerEligibilityHttpLookup lookup =
            new CustomerEligibilityHttpLookup(http, () -> actor, remoteContracts());

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

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

    private MerchantEligibilityDTO allowed(String payee) {
        return new MerchantEligibilityDTO(
                true,
                "ALLOWED",
                new MerchantProfileDTO(payee, "M001", "ENTERPRISE", "RETAIL", "CN", null, null, true),
                1,
                "grant",
                1L);
    }

    private void respond(MerchantEligibilityDTO value) {
        when(http.authorize(anyString(), any())).thenReturn(SuccessResponse.of(value));
    }

    @Test
    void delegatesTokenPerRequestAndAcceptsOnlyFactsForTheExpectedPayee() {
        respond(allowed("merchant"));
        assertTrue(lookup.authorizeNewOrder("merchant").allowed());
        verify(http)
                .authorize(
                        eq("Bearer " + TOKEN),
                        argThat(command -> command.payeeMerchantId().equals("merchant")
                                && command.action().name().equals("CREATE_ORDER")));
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("another-merchant"));
    }

    @Test
    void missingAudienceAndActiveTransactionsPreventNetworkAccess() {
        var missingAudience = new CustomerEligibilityHttpLookup(
                http,
                () -> new ProductActor("tenant", "MERCHANT_APP", "app", "merchant", null, Set.of()),
                remoteContracts());
        assertThrows(CommonException.class, () -> missingAudience.authorizeNewOrder("merchant"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("merchant"));
        verifyNoInteractions(http);
    }

    @Test
    void onlyWhitelistedNotFoundBecomesABusinessDenial() {
        when(http.authorize(anyString(), any()))
                .thenReturn(new SuccessResponse<>(Result.buildFailure(CustomerResultCodeEnum.NOT_FOUND), null));
        assertEquals(
                CustomerResultCodeEnum.NOT_FOUND.getCode(),
                lookup.authorizeNewOrder("merchant").reasonCode());
        when(http.authorize(anyString(), any()))
                .thenReturn(new SuccessResponse<>(Result.buildFailure(CustomerResultCodeEnum.ACTION_FORBIDDEN), null));
        var error = assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("merchant"));
        assertSame(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
        respond(new MerchantEligibilityDTO(
                false, CustomerResultCodeEnum.MERCHANT_INACTIVE.getCode(), null, 0, null, null));
        assertFalse(lookup.authorizeNewOrder("merchant").allowed());
    }

    @Test
    void malformedRepliesAndTransportFailuresStayDependencyFailures() {
        respond(null);
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("merchant"));
        respond(new MerchantEligibilityDTO(true, "ALLOWED", allowed("merchant").merchant(), 0, "grant", 1L));
        assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("merchant"));
        when(http.authorize(anyString(), any())).thenThrow(new IllegalStateException("sensitive remote detail"));
        var error = assertThrows(CommonException.class, () -> lookup.authorizeNewOrder("merchant"));
        assertSame(ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE, error.getResultCode());
        assertNull(error.getCause());
    }
}
