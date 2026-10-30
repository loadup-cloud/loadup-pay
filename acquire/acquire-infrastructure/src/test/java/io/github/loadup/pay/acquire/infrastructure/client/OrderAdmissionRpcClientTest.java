package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.rpc.proto.ProductRpc;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderAdmissionRpcClientTest {
    static final String TOKEN = "aGVhZGVy.eyJzdWIiOiJhY3F1aXJlIn0.c2lnbmF0dXJl";
    final ProductRpc productRpc = mock(ProductRpc.class);
    final AcquireActor actor = new AcquireActor("tenant", "a".repeat(64), TOKEN, Set.of());
    final OrderAdmissionRpcClient client = new OrderAdmissionRpcClient(() -> actor);
    final OrderAdmissionAuthorizeCommand command =
            new OrderAdmissionAuthorizeCommand("order", "merchant", "scope", "b".repeat(64), 100, "CNY", null);

    @BeforeEach
    void inject() {
        ReflectionTestUtils.setField(client, "productRpc", productRpc);
    }

    @Test
    void propagatesTheDelegatedTokenAsAClientAttachment() {
        doAnswer(invocation -> {
            assertEquals("Bearer " + TOKEN,
                    RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
            return io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO.getDefaultInstance();
        }).when(productRpc).authorize(any());
        assertNotNull(client.authorize(command));
        assertNull(RpcContext.getClientAttachment().getAttachment(RpcTokenContext.AUTHORIZATION));
    }

    @Test
    void authenticationFailureIsADependencyFailureRatherThanAPersistableContractDenial() {
        doAnswer(invocation -> {
            throw new RpcException(RpcException.BIZ_EXCEPTION, "loadup-result-code=" + ACTION_FORBIDDEN.getCode());
        }).when(productRpc).authorize(any());
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.authorize(command))
                        .getResultCode());
    }

    @Test
    void malformedResponseDoesNotExposeTheBodyOrToken() {
        doAnswer(invocation -> {
            throw new IllegalArgumentException("synthetic-secret payload");
        }).when(productRpc).authorize(any());
        var failure = assertThrows(CommonException.class, () -> client.authorize(command));
        assertSame(DEPENDENCY_UNAVAILABLE, failure.getResultCode());
        assertFalse(failure.toString().contains("synthetic-secret"));
        assertNull(failure.getCause());
    }

    @Test
    void missingDelegationAudiencePreventsAnyNetworkRequest() {
        var denied = new OrderAdmissionRpcClient(
                () -> new AcquireActor("tenant", "a".repeat(64), null, Set.of()));
        ReflectionTestUtils.setField(denied, "productRpc", productRpc);
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> denied.authorize(command))
                        .getResultCode());
        verifyNoInteractions(productRpc);
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
        verifyNoInteractions(productRpc);
    }
}
