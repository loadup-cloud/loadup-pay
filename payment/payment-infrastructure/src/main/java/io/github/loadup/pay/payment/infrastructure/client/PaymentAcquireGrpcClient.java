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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.components.rpc.grpc.RpcOperation;
import com.google.protobuf.Message;
import io.github.loadup.pay.payment.infrastructure.config.PaymentAcquireProperties;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.rpc.AcquireRpcOperations.ACQUIRE_PAYMENT_OUTCOME;
import static io.github.loadup.pay.acquire.rpc.AcquireRpcOperations.ACQUIRE_FEE_REFUND;

@Component
public class PaymentAcquireGrpcClient implements PaymentAcquireClient {
    private final GrpcTransport rpc;
    private final PaymentAcquireProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentAcquireGrpcClient(GrpcTransport rpc, JsonMapper mapper, PaymentAcquireProperties properties,
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
    public PaymentOutcomeAckDTO accept(String tenant, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return exchange(tenant, ACQUIRE_PAYMENT_OUTCOME, event);
    }

    @Override
    public FeeRefundAckDTO acceptFeeRefund(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, ACQUIRE_FEE_REFUND, event);
    }

    private <Q, R, P extends Message, S extends Message> R exchange(String tenant, RpcOperation<Q, R, P, S> operation, Q request) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            var response = rpc.call(operation, request, token);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
