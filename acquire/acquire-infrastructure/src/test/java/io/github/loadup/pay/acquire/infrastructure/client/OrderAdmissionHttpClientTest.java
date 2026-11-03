package io.github.loadup.pay.acquire.infrastructure.client;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.http.ProductHttpService;
import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrderAdmissionHttpClientTest {
    static final String TOKEN = "aGVhZGVy.eyJzdWIiOiJhY3F1aXJlIn0.c2lnbmF0dXJl";
    final ProductHttpService productHttp = mock(ProductHttpService.class);
    final AcquireActor actor = new AcquireActor("tenant", "a".repeat(64), TOKEN, Set.of());
    final OrderAdmissionHttpClient client = new OrderAdmissionHttpClient(productHttp, () -> actor);
    final OrderAdmissionAuthorizeCommand command =
            new OrderAdmissionAuthorizeCommand("order", "merchant", "scope", "b".repeat(64), 100, "CNY", null);

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
        assertNotNull(client.authorize(command));
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
                productHttp, () -> new AcquireActor("tenant", "a".repeat(64), null, Set.of()));
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
