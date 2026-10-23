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
package io.github.loadup.pay.limitcenter.rpc;

import io.github.loadup.components.rpc.grpc.RpcOperation;

public final class LimitCenterRpcOperations {
    private LimitCenterRpcOperations() {}

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand, io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivationDTO> LIMITCENTER_ACTIVATE =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getActivateMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitPolicyActivateCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitPolicyActivationDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand, io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReserveCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> LIMITCENTER_RESERVE =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getReserveMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitReserveCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitReservationDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery, io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitOperationQuery, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> LIMITCENTER_DETAIL =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getDetailMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitOperationQuery, LimitCenterRpcMapper.INSTANCE::fromWireLimitReservationDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand, io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitBeginExecutionCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionPermitDTO> LIMITCENTER_BEGIN =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getBeginMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitBeginExecutionCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitExecutionPermitDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand, io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerifyCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerificationDTO> LIMITCENTER_VERIFY =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getVerifyMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitExecutionVerifyCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitExecutionVerificationDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand, io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExpireCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> LIMITCENTER_EXPIRE =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getExpireMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitExpireCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitReservationDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand, io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitConfirmCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO> LIMITCENTER_CONFIRM =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getConfirmMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitConfirmCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitReservationDTO);

    public static final RpcOperation<io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand, io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseCommand, io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseDTO> LIMITCENTER_RELEASE =
            new RpcOperation<>("limitcenter", io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpcGrpc.getReleaseMethod(),
                    LimitCenterRpcMapper.INSTANCE::toWireLimitReleaseCommand, LimitCenterRpcMapper.INSTANCE::fromWireLimitReleaseDTO);
}
