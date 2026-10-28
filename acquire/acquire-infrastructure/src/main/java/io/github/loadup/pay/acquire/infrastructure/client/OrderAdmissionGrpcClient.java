/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.pay.acquire.app.port.OrderAdmissionClient;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.product.rpc.ProductRpcOperations.PRODUCT_AUTHORIZE;

@Component
public class OrderAdmissionGrpcClient implements OrderAdmissionClient {
    private final GrpcTransport rpc;
    private final AcquireActorProvider actors;

    public OrderAdmissionGrpcClient(GrpcTransport rpc, AcquireActorProvider actors) {
        this.rpc = rpc;
        this.actors = actors;
    }

    @Override
    public OrderAdmissionDTO authorize(OrderAdmissionAuthorizeCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor.productAccessToken() == null || actor.productAccessToken().isBlank())
            throw new CommonException(ACTION_FORBIDDEN);
        try {
            var response = rpc.call(PRODUCT_AUTHORIZE, command, actor.productAccessToken());
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
