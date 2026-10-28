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
package io.github.loadup.pay.reconciliation.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationFacade;
import io.github.loadup.pay.reconciliation.rpc.ReconciliationRpcMapper;
import io.github.loadup.pay.reconciliation.rpc.proto.ReconciliationRpcGrpc;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchQuery;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.rpc.proto.WireReconciliationInputAttachCommand;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class ReconciliationRpcConfiguration {
    @Bean
    BindableService reconciliationRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
                                      ReconciliationFacade facade, ReconciliationRpcMapper mapper) {
        var service = new ReconciliationRpcGrpc.ReconciliationRpcImplBase() {
            @Override
            public void create(WireReconciliationBatchCreateCommand request, StreamObserver<WireReconciliationBatchDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::create, mapper::toWire);
            }

            @Override
            public void attach(WireReconciliationInputAttachCommand request, StreamObserver<WireReconciliationBatchDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::attach, mapper::toWire);
            }

            @Override
            public void start(WireReconciliationBatchStartCommand request, StreamObserver<WireReconciliationBatchDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::start, mapper::toWire);
            }

            @Override
            public void detail(WireReconciliationBatchQuery request, StreamObserver<WireReconciliationBatchDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::detail, mapper::toWire);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
