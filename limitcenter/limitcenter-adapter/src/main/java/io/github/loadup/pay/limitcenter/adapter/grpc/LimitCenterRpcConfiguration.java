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
package io.github.loadup.pay.limitcenter.adapter.grpc;

import io.github.loadup.components.rpc.grpc.GrpcSupportConfiguration;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.components.rpc.grpc.RpcJwtInterceptor;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.grpc.BindableService;
import io.grpc.ServerInterceptors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(GrpcSupportConfiguration.class)
public class LimitCenterRpcConfiguration {
    @Bean
    BindableService limitCenterRpc(RpcServerSupport support, RpcJwtInterceptor authentication,
            io.github.loadup.pay.limitcenter.client.facade.LimitPolicyFacade facade0,
            io.github.loadup.pay.limitcenter.client.facade.LimitReservationFacade facade1,
            io.github.loadup.pay.limitcenter.client.facade.LimitExecutionFacade facade2,
            io.github.loadup.pay.limitcenter.client.facade.LimitSettlementFacade facade3) {
        var service = new io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.LimitCenterRpcImplBase() {
            @Override
            public void activate(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivationDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitPolicyActivateCommand,
                        facade0::activate, LimitCenterRpcMapper.INSTANCE::toWireLimitPolicyActivationDTO);
            }
            @Override
            public void reserve(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReserveCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitReserveCommand,
                        facade1::reserve, LimitCenterRpcMapper.INSTANCE::toWireLimitReservationDTO);
            }
            @Override
            public void detail(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitOperationQuery request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitOperationQuery,
                        facade1::detail, LimitCenterRpcMapper.INSTANCE::toWireLimitReservationDTO);
            }
            @Override
            public void begin(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitBeginExecutionCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionPermitDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitBeginExecutionCommand,
                        facade2::beginExecution, LimitCenterRpcMapper.INSTANCE::toWireLimitExecutionPermitDTO);
            }
            @Override
            public void verify(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerifyCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerificationDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitExecutionVerifyCommand,
                        facade2::verifyExecution, LimitCenterRpcMapper.INSTANCE::toWireLimitExecutionVerificationDTO);
            }
            @Override
            public void expire(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExpireCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitExpireCommand,
                        facade2::expire, LimitCenterRpcMapper.INSTANCE::toWireLimitReservationDTO);
            }
            @Override
            public void confirm(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitConfirmCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitConfirmCommand,
                        facade3::confirm, LimitCenterRpcMapper.INSTANCE::toWireLimitReservationDTO);
            }
            @Override
            public void release(io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseCommand request, io.grpc.stub.StreamObserver<io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseDTO> observer) {
                support.invoke(request, observer, LimitCenterRpcMapper.INSTANCE::fromWireLimitReleaseCommand,
                        facade3::release, LimitCenterRpcMapper.INSTANCE::toWireLimitReleaseDTO);
            }
        };
        return () -> ServerInterceptors.intercept(service, authentication);
    }
}
