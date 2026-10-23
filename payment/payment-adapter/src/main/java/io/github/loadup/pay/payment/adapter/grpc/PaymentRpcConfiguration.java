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
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class PaymentRpcConfiguration {
    @Bean
    BindableService paymentRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
            io.github.loadup.pay.payment.client.facade.PaymentInputFacade facade0,
            io.github.loadup.pay.payment.client.facade.PaymentAcceptanceFacade facade1,
            io.github.loadup.pay.payment.client.facade.PaymentOutcomeFacade facade2,
            io.github.loadup.pay.payment.client.facade.PaymentSettlementFactsFacade facade3,
            io.github.loadup.pay.payment.client.facade.PaymentControlFacade facade4,
            io.github.loadup.pay.payment.client.facade.PaymentAttemptFacade facade5,
            io.github.loadup.pay.payment.client.facade.RefundSummaryFactsFacade facade6,
            io.github.loadup.pay.payment.client.facade.RefundSettlementFactsFacade facade7) {
        var service = new io.github.loadup.pay.payment.rpc.proto.PaymentRpcGrpc.PaymentRpcImplBase() {
            @Override
            public void inputPrepare(io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentInputDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWirePaymentInputPrepareCommand,
                        facade0::prepare, PaymentRpcMapper.INSTANCE::toWirePaymentInputDTO);
            }
            @Override
            public void accept(io.github.loadup.pay.payment.rpc.proto.WirePaymentExecuteCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWirePaymentExecuteCommand,
                        facade1::accept, PaymentRpcMapper.INSTANCE::toWirePaymentExecutionDTO);
            }
            @Override
            public void detail(io.github.loadup.pay.payment.rpc.proto.WireIdQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireIdQuery,
                        facade1::detail, PaymentRpcMapper.INSTANCE::toWirePaymentExecutionDTO);
            }
            @Override
            public void outcome(io.github.loadup.pay.payment.rpc.proto.WireIdQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentOutcomeDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireIdQuery,
                        facade2::outcome, PaymentRpcMapper.INSTANCE::toWirePaymentOutcomeDTO);
            }
            @Override
            public void facts(io.github.loadup.pay.payment.rpc.proto.WireIdQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentSettlementFactsDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireIdQuery,
                        facade3::paymentFacts, PaymentRpcMapper.INSTANCE::toWirePaymentSettlementFactsDTO);
            }
            @Override
            public void close(io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentCloseDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWirePaymentCloseCommand,
                        facade4::close, PaymentRpcMapper.INSTANCE::toWirePaymentCloseDTO);
            }
            @Override
            public void fenceParent(io.github.loadup.pay.payment.rpc.proto.WireParentPaymentFenceCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireParentPaymentFenceCommand,
                        facade4::fenceParent, PaymentRpcMapper.INSTANCE::toWireParentFenceDTO);
            }
            @Override
            public void parentFenceStatus(io.github.loadup.pay.payment.rpc.proto.WireParentFenceStatusQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WireParentFenceDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireParentFenceStatusQuery,
                        facade4::parentFenceStatus, PaymentRpcMapper.INSTANCE::toWireParentFenceDTO);
            }
            @Override
            public void startAttempt(io.github.loadup.pay.payment.rpc.proto.WirePaymentAttemptStartCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WirePaymentExecutionDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWirePaymentAttemptStartCommand,
                        facade5::startAttempt, PaymentRpcMapper.INSTANCE::toWirePaymentExecutionDTO);
            }
            @Override
            public void refundSummary(io.github.loadup.pay.payment.rpc.proto.WireIdQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WireOrderRefundSummaryEventDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireIdQuery,
                        facade6::refundSummary, PaymentRpcMapper.INSTANCE::toWireOrderRefundSummaryEventDTO);
            }
            @Override
            public void refundFacts(io.github.loadup.pay.payment.rpc.proto.WireIdQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.payment.rpc.proto.WireRefundSettlementFactsDTO> observer) {
                support.invoke(request, observer, PaymentRpcMapper.INSTANCE::fromWireIdQuery,
                        facade7::refundFacts, PaymentRpcMapper.INSTANCE::toWireRefundSettlementFactsDTO);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
