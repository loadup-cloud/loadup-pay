package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.dubbo.RpcExceptionCodes;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcMapper;
import io.github.loadup.pay.limitcenter.rpc.proto.LimitCenterRpc;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;

@Component
public class PaymentLimitRpcClient implements PaymentLimitClient {
    @DubboReference(url = "tri://limitcenter-headless:50051")
    private LimitCenterRpc limitCenterRpc;

    private final PaymentLimitProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentLimitRpcClient(JsonMapper mapper, PaymentLimitProperties properties,
                                 @Qualifier("paymentClock") Clock clock) {
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitReservationDTO reserve(String tenant, LimitReserveCommand command) {
        return exchange(tenant, () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitReservationDTO(
                limitCenterRpc.reserve(LimitCenterRpcMapper.INSTANCE.toWireLimitReserveCommand(command))));
    }

    @Override
    public LimitExecutionPermitDTO begin(String tenant, LimitBeginExecutionCommand command) {
        return exchange(tenant, () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitExecutionPermitDTO(
                limitCenterRpc.begin(LimitCenterRpcMapper.INSTANCE.toWireLimitBeginExecutionCommand(command))));
    }

    @Override
    public LimitExecutionVerificationDTO verify(String tenant, LimitExecutionVerifyCommand command) {
        return exchange(tenant, () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitExecutionVerificationDTO(
                limitCenterRpc.verify(LimitCenterRpcMapper.INSTANCE.toWireLimitExecutionVerifyCommand(command))));
    }

    @Override
    public LimitReservationDTO confirm(String tenant, LimitConfirmCommand command) {
        return exchange(tenant, () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitReservationDTO(
                limitCenterRpc.confirm(LimitCenterRpcMapper.INSTANCE.toWireLimitConfirmCommand(command))));
    }

    @Override
    public LimitReleaseDTO release(String tenant, LimitReleaseCommand command) {
        return exchange(tenant, () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitReleaseDTO(
                limitCenterRpc.release(LimitCenterRpcMapper.INSTANCE.toWireLimitReleaseCommand(command))));
    }

    @Override
    public LimitRestoreDTO restoreRefund(String tenant, LimitRefundRestoreCommand command) {
        return exchange(tenant, () -> LimitCenterRpcMapper.INSTANCE.fromWireLimitRestoreDTO(
                limitCenterRpc.restoreRefund(LimitCenterRpcMapper.INSTANCE.toWireLimitRefundRestoreCommand(command))));
    }

    private <R> R exchange(String tenant, Supplier<R> call) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            R response = RpcTokenContext.call(credential(tenant), call);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            var code = switch (RpcExceptionCodes.resultCode(failure)) {
                case "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED;
                case "LIMIT_RESERVATION_EXPIRED" -> LIMIT_RESERVATION_EXPIRED;
                case "LIMIT_EXECUTION_FENCED" -> EXECUTION_FENCED;
                case "LIMIT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                default -> DEPENDENCY_UNAVAILABLE;
            };
            throw new CommonException(code);
        }
    }

    private String credential(String tenant) {
        return tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
    }
}
