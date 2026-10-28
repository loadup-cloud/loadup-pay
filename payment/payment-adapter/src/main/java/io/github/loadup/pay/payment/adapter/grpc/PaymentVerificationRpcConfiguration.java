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
import io.github.loadup.pay.payment.client.facade.PaymentVerificationFacade;
import io.github.loadup.pay.payment.rpc.PaymentVerificationRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentVerificationRpcGrpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentVerificationQuery;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class PaymentVerificationRpcConfiguration {
    @Bean
    BindableService paymentVerificationRpc(RpcServerSupport support, RpcJwtInterceptor authentication, PaymentVerificationFacade facade, PaymentVerificationRpcMapper mapper) {
        var service = new PaymentVerificationRpcGrpc.PaymentVerificationRpcImplBase() {
            @Override
            public void verify(WirePaymentVerificationQuery request, StreamObserver<WirePaymentVerificationDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::verify, mapper::toWire);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
