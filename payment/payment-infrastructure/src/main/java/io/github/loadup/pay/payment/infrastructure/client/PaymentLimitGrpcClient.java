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
package io.github.loadup.pay.payment.infrastructure.client;

import com.google.protobuf.Message;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcOperation;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
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
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_BEGIN;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_CONFIRM;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_RELEASE;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_RESERVE;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_RESTORE_REFUND;
import static io.github.loadup.pay.limitcenter.rpc.LimitCenterRpcOperations.LIMITCENTER_VERIFY;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;

@Component
public class PaymentLimitGrpcClient implements PaymentLimitClient {
    private final GrpcTransport rpc;
    private final PaymentLimitProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentLimitGrpcClient(GrpcTransport rpc, JsonMapper mapper, PaymentLimitProperties properties,
                                  @Qualifier("paymentClock") Clock clock) {
        this.rpc = rpc;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitReservationDTO reserve(String tenant, LimitReserveCommand command) {
        return exchange(tenant, LIMITCENTER_RESERVE, command);
    }

    @Override
    public LimitExecutionPermitDTO begin(String tenant, LimitBeginExecutionCommand command) {
        return exchange(tenant, LIMITCENTER_BEGIN, command);
    }

    @Override
    public LimitExecutionVerificationDTO verify(String tenant, LimitExecutionVerifyCommand command) {
        return exchange(tenant, LIMITCENTER_VERIFY, command);
    }

    @Override
    public LimitReservationDTO confirm(String tenant, LimitConfirmCommand command) {
        return exchange(tenant, LIMITCENTER_CONFIRM, command);
    }

    @Override
    public LimitReleaseDTO release(String tenant, LimitReleaseCommand command) {
        return exchange(tenant, LIMITCENTER_RELEASE, command);
    }

    @Override
    public LimitRestoreDTO restoreRefund(String tenant, LimitRefundRestoreCommand command) {
        return exchange(tenant, LIMITCENTER_RESTORE_REFUND, command);
    }

    private <Q, R, P extends Message, S extends Message> R exchange(
            String tenant, RpcOperation<Q, R, P, S> operation, Q command) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            R response = rpc.call(operation, command, credential(tenant));
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            var code = switch (RpcServerSupport.resultCode(failure)) {
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
