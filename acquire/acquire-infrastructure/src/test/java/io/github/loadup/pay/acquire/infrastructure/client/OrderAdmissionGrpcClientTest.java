/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     rpc://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.product.rpc.ProductRpcOperations.PRODUCT_AUTHORIZE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderAdmissionGrpcClientTest {
    final GrpcTransport rpc = mock(GrpcTransport.class);
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final AcquireActor actor = new AcquireActor("tenant", "a".repeat(64), "synthetic-secret", Set.of());
    final OrderAdmissionGrpcClient client = new OrderAdmissionGrpcClient(rpc, () -> actor);
    final OrderAdmissionAuthorizeCommand command =
            new OrderAdmissionAuthorizeCommand("order", "merchant", "scope", "b".repeat(64), 100, "CNY", null);

    @Test
    void authenticationFailureIsADependencyFailureRatherThanAPersistableContractDenial() {
        when(rpc.call(eq(PRODUCT_AUTHORIZE), any(), any()))
                .thenThrow(remoteFailure((ACTION_FORBIDDEN).getCode()));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> client.authorize(command))
                        .getResultCode());
    }

    @Test
    void malformedResponseDoesNotExposeTheBodyOrToken() {
        when(rpc.call(eq(PRODUCT_AUTHORIZE), any(), any()))
                .thenThrow(new IllegalArgumentException("synthetic-secret payload"));
        var failure = assertThrows(CommonException.class, () -> client.authorize(command));
        assertSame(DEPENDENCY_UNAVAILABLE, failure.getResultCode());
        assertFalse(failure.toString().contains("synthetic-secret"));
        assertNull(failure.getCause());
    }

    @Test
    void missingDelegationAudiencePreventsAnyNetworkRequest() {
        var denied = new OrderAdmissionGrpcClient(
                rpc, () -> new AcquireActor("tenant", "a".repeat(64), null, Set.of()));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> denied.authorize(command))
                        .getResultCode());
        verifyNoInteractions(rpc);
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
        verifyNoInteractions(rpc);
    }
    private static io.grpc.StatusRuntimeException remoteFailure(String code) {
        var trailers = new Metadata();
        trailers.put(RpcServerSupport.RESULT_CODE, code);
        return Status.FAILED_PRECONDITION.asRuntimeException(trailers);
    }
}
