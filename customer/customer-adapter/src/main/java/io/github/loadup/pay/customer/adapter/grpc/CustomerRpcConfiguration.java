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
package io.github.loadup.pay.customer.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.customer.rpc.CustomerRpcMapper;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class CustomerRpcConfiguration {
    @Bean
    BindableService customerRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
                                io.github.loadup.pay.customer.client.facade.MerchantEligibilityFacade facade0) {
        var service = new io.github.loadup.pay.customer.rpc.proto.CustomerRpcGrpc.CustomerRpcImplBase() {
            @Override
            public void authorize(io.github.loadup.pay.customer.rpc.proto.WireMerchantActionAuthorizeCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.customer.rpc.proto.WireMerchantEligibilityDTO> observer) {
                support.invoke(request, observer, CustomerRpcMapper.INSTANCE::fromWireMerchantActionAuthorizeCommand,
                        facade0::authorize, CustomerRpcMapper.INSTANCE::toWireMerchantEligibilityDTO);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
