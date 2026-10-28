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
package io.github.loadup.pay.realtime.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.realtime.rpc.RealtimeRpcMapper;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class RealtimeRpcConfiguration {
    @Bean
    BindableService realtimeRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
                                io.github.loadup.pay.realtime.client.facade.TradeProjectionFacade facade0) {
        var service = new io.github.loadup.pay.realtime.rpc.proto.RealtimeRpcGrpc.RealtimeRpcImplBase() {
            @Override
            public void order(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderSnapshotEventDTO request, io.grpc.stub.StreamObserver<io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> observer) {
                support.invoke(request, observer, RealtimeRpcMapper.INSTANCE::fromWirePayEventEnvelopeOfOrderSnapshotEventDTO,
                        facade0::applyOrder, RealtimeRpcMapper.INSTANCE::toWireProjectionAckDTO);
            }

            @Override
            public void payment(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfPaymentSnapshotEventDTO request, io.grpc.stub.StreamObserver<io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> observer) {
                support.invoke(request, observer, RealtimeRpcMapper.INSTANCE::fromWirePayEventEnvelopeOfPaymentSnapshotEventDTO,
                        facade0::applyPayment, RealtimeRpcMapper.INSTANCE::toWireProjectionAckDTO);
            }

            @Override
            public void refund(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfRefundSnapshotEventDTO request, io.grpc.stub.StreamObserver<io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> observer) {
                support.invoke(request, observer, RealtimeRpcMapper.INSTANCE::fromWirePayEventEnvelopeOfRefundSnapshotEventDTO,
                        facade0::applyRefund, RealtimeRpcMapper.INSTANCE::toWireProjectionAckDTO);
            }

            @Override
            public void refundSummary(io.github.loadup.pay.realtime.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO request, io.grpc.stub.StreamObserver<io.github.loadup.pay.realtime.rpc.proto.WireProjectionAckDTO> observer) {
                support.invoke(request, observer, RealtimeRpcMapper.INSTANCE::fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO,
                        facade0::applyRefundSummary, RealtimeRpcMapper.INSTANCE::toWireProjectionAckDTO);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
