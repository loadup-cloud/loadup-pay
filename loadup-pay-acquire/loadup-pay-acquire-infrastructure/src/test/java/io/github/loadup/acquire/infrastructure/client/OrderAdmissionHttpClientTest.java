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
package io.github.loadup.acquire.infrastructure.client;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.acquire.app.model.productcenter.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.acquire.app.security.AcquireActor;
import io.github.loadup.acquire.infrastructure.converter.RemoteContractConverter;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.productcenter.client.dto.OrderAdmissionDTO;
import io.github.loadup.productcenter.client.enums.AdmissionDecision;
import io.github.loadup.productcenter.client.http.ProductHttpService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RemoteContractMapperTestConfiguration.class)
class OrderAdmissionHttpClientTest {
    static final String TOKEN = "aGVhZGVy.eyJzdWIiOiJhY3F1aXJlIn0.c2lnbmF0dXJl";
    final ProductHttpService productHttp = mock(ProductHttpService.class);
    final AcquireActor actor = new AcquireActor("tenant", "a".repeat(64), TOKEN, Set.of());
    final OrderAdmissionAuthorizeCommand command =
            new OrderAdmissionAuthorizeCommand("order", "merchant", "scope", "b".repeat(64), 100, "CNY", null);

    @Autowired
    RemoteContractConverter contracts;

    OrderAdmissionHttpClient client;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        client = new OrderAdmissionHttpClient(productHttp, () -> actor, contracts);
    }

    @Test
    void propagatesTheDelegatedTokenPerHttpRequest() {
        doAnswer(invocation -> {
                    assertEquals("Bearer " + TOKEN, invocation.getArgument(0, String.class));
                    return SuccessResponse.of(new OrderAdmissionDTO(
                            "admission",
                            "order",
                            "merchant",
                            AdmissionDecision.DENIED,
                            "PRODUCT_CONTRACT_UNAVAILABLE",
                            "b".repeat(64),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
                })
                .when(productHttp)
                .authorize(anyString(), any());
        var result = client.authorize(command);
        assertEquals("order", result.orderId());
        assertEquals("PRODUCT_CONTRACT_UNAVAILABLE", result.reasonCode());
        var request = ArgumentCaptor.forClass(
                io.github.loadup.productcenter.client.command.OrderAdmissionAuthorizeCommand.class);
        Mockito.verify(productHttp).authorize("Bearer " + TOKEN, request.capture());
        assertEquals("order", request.getValue().orderId());
        assertEquals("merchant", request.getValue().payeeMerchantId());
        assertEquals("scope", request.getValue().productScopeKey());
    }

    @Test
    void authenticationFailureIsADependencyFailureRatherThanAPersistableContractDenial() {
        doAnswer(invocation -> {
                    return new SuccessResponse<>(Result.buildFailure(CommonResultCodeEnum.UNAUTHENTICATED), null);
                })
                .when(productHttp)
                .authorize(anyString(), any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.authorize(command))
                        .getResultCode());
    }

    @Test
    void malformedResponseDoesNotExposeTheBodyOrToken() {
        doAnswer(invocation -> {
                    throw new IllegalArgumentException("synthetic-secret payload");
                })
                .when(productHttp)
                .authorize(anyString(), any());
        var failure = assertThrows(CommonException.class, () -> client.authorize(command));
        assertSame(DEPENDENCY_UNAVAILABLE, failure.getResultCode());
        assertFalse(failure.toString().contains("synthetic-secret"));
        assertNull(failure.getCause());
    }

    @Test
    void missingDelegationAudiencePreventsAnyNetworkRequest() {
        var denied = new OrderAdmissionHttpClient(
                productHttp, () -> new AcquireActor("tenant", "a".repeat(64), null, Set.of()), contracts);
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> denied.authorize(command))
                        .getResultCode());
        verifyNoInteractions(productHttp);
    }

    @Test
    void networkCannotJoinTheLocalCreationTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> client.authorize(command))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(productHttp);
    }
}
