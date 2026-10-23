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
package io.github.loadup.pay.acquire.infrastructure.client;

import com.google.protobuf.Message;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentClosureClient;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcOperation;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.PAYMENT_INPUT_EXPIRED;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_ACCEPT;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_CLOSE;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_DETAIL;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_FACTS;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_FENCE_PARENT;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_INPUT_PREPARE;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_OUTCOME;
import static io.github.loadup.pay.payment.rpc.PaymentRpcOperations.PAYMENT_PARENT_FENCE_STATUS;

@Component
public class PaymentAcceptanceGrpcClient implements PaymentAcceptanceClient, PaymentOutcomeClient, PaymentClosureClient {
    private final GrpcTransport rpc;
    private final PaymentTransportProperties properties;
    private final AcquireServiceTokenReader tokens;

    public PaymentAcceptanceGrpcClient(
            GrpcTransport rpc,
            JsonMapper mapper,
            PaymentTransportProperties properties,
            @Qualifier("acquireClock") Clock clock) {
        this.rpc = rpc;
        this.properties = properties;
        this.tokens = new AcquireServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentInputDTO prepareInput(String tenant, PaymentInputPrepareCommand command) {
        return exchange(tenant, PAYMENT_INPUT_PREPARE, command);
    }

    @Override
    public PaymentExecutionDTO accept(String tenant, PaymentExecuteCommand command) {
        return exchange(tenant, PAYMENT_ACCEPT, command);
    }

    @Override
    public PaymentExecutionDTO detail(String tenant, String paymentId) {
        return exchange(tenant, PAYMENT_DETAIL, new IdQuery(paymentId));
    }

    @Override
    public PaymentOutcomeDTO outcome(String tenant, String paymentId) {
        return exchange(tenant, PAYMENT_OUTCOME, new IdQuery(paymentId));
    }

    @Override
    public PaymentSettlementFactsDTO facts(String tenant, String paymentId) {
        return exchange(tenant, PAYMENT_FACTS, new IdQuery(paymentId));
    }

    @Override
    public ParentFenceDTO fenceParent(String tenant, ParentPaymentFenceCommand command) {
        return exchange(tenant, PAYMENT_FENCE_PARENT, command);
    }

    @Override
    public PaymentCloseDTO close(String tenant, PaymentCloseCommand command) {
        return exchange(tenant, PAYMENT_CLOSE, command);
    }

    @Override
    public ParentFenceDTO parentFenceStatus(String tenant, ParentFenceStatusQuery query) {
        return exchange(tenant, PAYMENT_PARENT_FENCE_STATUS, query);
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
            if (operation.descriptor().equals(PAYMENT_INPUT_PREPARE.descriptor()) || operation.descriptor().equals(PAYMENT_ACCEPT.descriptor())) {
                var code = switch (RpcServerSupport.resultCode(failure)) {
                    case "PAYMENT_IDEMPOTENCY_CONFLICT", "PAYMENT_INPUT_ALREADY_BOUND" -> IDEMPOTENCY_CONFLICT;
                    case "PAYMENT_INPUT_EXPIRED" -> PAYMENT_INPUT_EXPIRED;
                    case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                    default -> DEPENDENCY_UNAVAILABLE;
                };
                throw new CommonException(code);
            }
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
    private String credential(String tenant) {
        return tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
    }
}
