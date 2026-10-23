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
package io.github.loadup.pay.acquire.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.pay.acquire.rpc.AcquireRpcMapper;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class AcquireRpcConfiguration {
    @Bean
    BindableService acquireRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
            io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade facade0,
            io.github.loadup.pay.acquire.client.facade.FeeRefundOutcomeFacade facade1) {
        var service = new io.github.loadup.pay.acquire.rpc.proto.AcquireRpcGrpc.AcquireRpcImplBase() {
            @Override
            public void paymentOutcome(io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfPaymentOutcomeEventDTO request, io.grpc.stub.StreamObserver<io.github.loadup.pay.acquire.rpc.proto.WirePaymentOutcomeAckDTO> observer) {
                support.invoke(request, observer, AcquireRpcMapper.INSTANCE::fromWirePayEventEnvelopeOfPaymentOutcomeEventDTO,
                        facade0::accept, AcquireRpcMapper.INSTANCE::toWirePaymentOutcomeAckDTO);
            }
            @Override
            public void feeRefund(io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO request, io.grpc.stub.StreamObserver<io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO> observer) {
                support.invoke(request, observer, AcquireRpcMapper.INSTANCE::fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO,
                        facade1::accept, AcquireRpcMapper.INSTANCE::toWireFeeRefundAckDTO);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
