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
package io.github.loadup.pay.payment.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.payment.client.facade.PaymentStatementFacade;
import io.github.loadup.pay.payment.rpc.PaymentStatementRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentStatementRpcGrpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentStatementQuery;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class PaymentStatementRpcConfiguration {
    @Bean
    BindableService paymentStatementRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
                                        PaymentStatementFacade facade, PaymentStatementRpcMapper mapper) {
        var service = new PaymentStatementRpcGrpc.PaymentStatementRpcImplBase() {
            @Override
            public void download(WirePaymentStatementQuery request, StreamObserver<WirePaymentStatementDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::download, mapper::toWire);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
