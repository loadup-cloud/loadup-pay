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
import io.github.loadup.pay.payment.client.facade.PaymentCaptureFacade;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentCaptureRpcGrpc;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCapturePage;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureQuery;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentCaptureRetryCommand;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import io.grpc.stub.StreamObserver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class PaymentCaptureRpcConfiguration {
    @Bean
    BindableService paymentCaptureRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
                                      PaymentCaptureFacade facade, PaymentCaptureRpcMapper mapper) {
        var service = new PaymentCaptureRpcGrpc.PaymentCaptureRpcImplBase() {
            @Override
            public void create(WirePaymentCaptureCreateCommand request, StreamObserver<WirePaymentCaptureDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::create, mapper::toWire);
            }

            @Override
            public void retry(WirePaymentCaptureRetryCommand request, StreamObserver<WirePaymentCaptureDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::retry, mapper::toWire);
            }

            @Override
            public void detail(WirePaymentCaptureQuery request, StreamObserver<WirePaymentCaptureDTO> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::detail, mapper::toWire);
            }

            @Override
            public void lines(WirePaymentCaptureLinesQuery request, StreamObserver<WirePaymentCapturePage> observer) {
                support.invoke(request, observer, mapper::fromWire, facade::lines, mapper::toWire);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
