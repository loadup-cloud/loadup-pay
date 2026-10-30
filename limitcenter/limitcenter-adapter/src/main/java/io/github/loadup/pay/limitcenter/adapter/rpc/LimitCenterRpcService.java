package io.github.loadup.pay.limitcenter.adapter.rpc;

import io.github.loadup.pay.limitcenter.client.facade.LimitExecutionFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitPolicyFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitRefundFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitReservationFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitSettlementFacade;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.github.loadup.pay.limitcenter.rpc.proto.DubboLimitCenterRpcTriple;
import io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpc;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitConfirmCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitExpireCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitOperationQuery;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReleaseDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReservationDTO;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitReserveCommand;
import io.github.loadup.pay.limitcenter.rpc.proto.WireLimitRestoreDTO;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Protobuf IDL entry point for the limitcenter service.
 */
@DubboService(interfaceClass = LimitCenterRpc.class)
public class LimitCenterRpcService extends DubboLimitCenterRpcTriple.LimitCenterRpcImplBase {
    private final LimitPolicyFacade limitPolicy;
    private final LimitReservationFacade limitReservation;
    private final LimitExecutionFacade limitExecution;
    private final LimitSettlementFacade limitSettlement;
    private final LimitRefundFacade limitRefund;

    private final LimitCenterRpcMapper limitCenterRpcMapper;

    public LimitCenterRpcService(
            LimitCenterRpcMapper limitCenterRpcMapper,
            LimitPolicyFacade limitPolicy,
            LimitReservationFacade limitReservation,
            LimitExecutionFacade limitExecution,
            LimitSettlementFacade limitSettlement,
            LimitRefundFacade limitRefund) {
        this.limitCenterRpcMapper = limitCenterRpcMapper;
        this.limitPolicy = limitPolicy;
        this.limitReservation = limitReservation;
        this.limitExecution = limitExecution;
        this.limitSettlement = limitSettlement;
        this.limitRefund = limitRefund;
    }

    @Override
    public WireLimitPolicyActivationDTO activate(WireLimitPolicyActivateCommand request) {
        return limitCenterRpcMapper.toWireLimitPolicyActivationDTO(
                limitPolicy.activate(limitCenterRpcMapper.fromWireLimitPolicyActivateCommand(request)));
    }

    @Override
    public WireLimitReservationDTO reserve(WireLimitReserveCommand request) {
        return limitCenterRpcMapper.toWireLimitReservationDTO(
                limitReservation.reserve(limitCenterRpcMapper.fromWireLimitReserveCommand(request)));
    }

    @Override
    public WireLimitReservationDTO detail(WireLimitOperationQuery request) {
        return limitCenterRpcMapper.toWireLimitReservationDTO(
                limitReservation.detail(limitCenterRpcMapper.fromWireLimitOperationQuery(request)));
    }

    @Override
    public WireLimitExecutionPermitDTO begin(WireLimitBeginExecutionCommand request) {
        return limitCenterRpcMapper.toWireLimitExecutionPermitDTO(
                limitExecution.beginExecution(limitCenterRpcMapper.fromWireLimitBeginExecutionCommand(request)));
    }

    @Override
    public WireLimitExecutionVerificationDTO verify(WireLimitExecutionVerifyCommand request) {
        return limitCenterRpcMapper.toWireLimitExecutionVerificationDTO(
                limitExecution.verifyExecution(limitCenterRpcMapper.fromWireLimitExecutionVerifyCommand(request)));
    }

    @Override
    public WireLimitReservationDTO expire(WireLimitExpireCommand request) {
        return limitCenterRpcMapper.toWireLimitReservationDTO(
                limitExecution.expire(limitCenterRpcMapper.fromWireLimitExpireCommand(request)));
    }

    @Override
    public WireLimitReservationDTO confirm(WireLimitConfirmCommand request) {
        return limitCenterRpcMapper.toWireLimitReservationDTO(
                limitSettlement.confirm(limitCenterRpcMapper.fromWireLimitConfirmCommand(request)));
    }

    @Override
    public WireLimitReleaseDTO release(WireLimitReleaseCommand request) {
        return limitCenterRpcMapper.toWireLimitReleaseDTO(
                limitSettlement.release(limitCenterRpcMapper.fromWireLimitReleaseCommand(request)));
    }

    @Override
    public WireLimitRestoreDTO restoreRefund(WireLimitRefundRestoreCommand request) {
        return limitCenterRpcMapper.toWireLimitRestoreDTO(
                limitRefund.restoreRefund(limitCenterRpcMapper.fromWireLimitRefundRestoreCommand(request)));
    }
}
