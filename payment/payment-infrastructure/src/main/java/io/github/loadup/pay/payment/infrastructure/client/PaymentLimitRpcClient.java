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
    private final PaymentLimitProperties properties;
    private final PaymentServiceTokenReader tokens;
    private final LimitCenterRpcMapper limitCenterRpcMapper;
    @DubboReference(url = "tri://limitcenter-headless:50051")
    private LimitCenterRpc limitCenterRpc;

    public PaymentLimitRpcClient(LimitCenterRpcMapper limitCenterRpcMapper, JsonMapper mapper, PaymentLimitProperties properties,
                                 Clock clock) {
        this.limitCenterRpcMapper = limitCenterRpcMapper;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitReservationDTO reserve(String tenant, LimitReserveCommand command) {
        return exchange(tenant, () -> limitCenterRpcMapper.fromWireLimitReservationDTO(
                limitCenterRpc.reserve(limitCenterRpcMapper.toWireLimitReserveCommand(command))));
    }

    @Override
    public LimitExecutionPermitDTO begin(String tenant, LimitBeginExecutionCommand command) {
        return exchange(tenant, () -> limitCenterRpcMapper.fromWireLimitExecutionPermitDTO(
                limitCenterRpc.begin(limitCenterRpcMapper.toWireLimitBeginExecutionCommand(command))));
    }

    @Override
    public LimitExecutionVerificationDTO verify(String tenant, LimitExecutionVerifyCommand command) {
        return exchange(tenant, () -> limitCenterRpcMapper.fromWireLimitExecutionVerificationDTO(
                limitCenterRpc.verify(limitCenterRpcMapper.toWireLimitExecutionVerifyCommand(command))));
    }

    @Override
    public LimitReservationDTO confirm(String tenant, LimitConfirmCommand command) {
        return exchange(tenant, () -> limitCenterRpcMapper.fromWireLimitReservationDTO(
                limitCenterRpc.confirm(limitCenterRpcMapper.toWireLimitConfirmCommand(command))));
    }

    @Override
    public LimitReleaseDTO release(String tenant, LimitReleaseCommand command) {
        return exchange(tenant, () -> limitCenterRpcMapper.fromWireLimitReleaseDTO(
                limitCenterRpc.release(limitCenterRpcMapper.toWireLimitReleaseCommand(command))));
    }

    @Override
    public LimitRestoreDTO restoreRefund(String tenant, LimitRefundRestoreCommand command) {
        return exchange(tenant, () -> limitCenterRpcMapper.fromWireLimitRestoreDTO(
                limitCenterRpc.restoreRefund(limitCenterRpcMapper.toWireLimitRefundRestoreCommand(command))));
    }

    private <R> R exchange(String tenant, Supplier<R> call) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            R response = RpcTokenContext.call(credential(tenant), call);
            if (response == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
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
