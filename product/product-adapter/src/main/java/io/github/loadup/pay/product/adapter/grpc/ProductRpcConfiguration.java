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
package io.github.loadup.pay.product.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.product.rpc.ProductRpcMapper;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class ProductRpcConfiguration {
    @Bean
    BindableService productRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
                               io.github.loadup.pay.product.client.facade.OrderAdmissionFacade facade0) {
        var service = new io.github.loadup.pay.product.rpc.proto.ProductRpcGrpc.ProductRpcImplBase() {
            @Override
            public void authorize(io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionAuthorizeCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO> observer) {
                support.invoke(request, observer, ProductRpcMapper.INSTANCE::fromWireOrderAdmissionAuthorizeCommand,
                        facade0::authorize, ProductRpcMapper.INSTANCE::toWireOrderAdmissionDTO);
            }

            @Override
            public void detail(io.github.loadup.pay.product.rpc.proto.WireIdQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.product.rpc.proto.WireOrderAdmissionDTO> observer) {
                support.invoke(request, observer, ProductRpcMapper.INSTANCE::fromWireIdQuery,
                        facade0::detail, ProductRpcMapper.INSTANCE::toWireOrderAdmissionDTO);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
