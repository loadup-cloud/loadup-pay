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
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcOperation;
import io.github.loadup.pay.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_PAYMENT;
import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_REFUND;
import static io.github.loadup.pay.realtime.rpc.RealtimeRpcOperations.REALTIME_REFUND_SUMMARY;

@Component
public class PaymentRealtimeGrpcClient implements PaymentRealtimeClient {
    private final GrpcTransport rpc;
    private final PaymentRealtimeProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentRealtimeGrpcClient(GrpcTransport rpc, JsonMapper mapper, PaymentRealtimeProperties properties,
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
    public ProjectionAckDTO apply(String tenant, EventEnvelope<PaymentSnapshotEventDTO> event) {
        return exchange(tenant, REALTIME_PAYMENT, event);
    }

    @Override
    public ProjectionAckDTO applyRefund(String tenant, EventEnvelope<RefundSnapshotEventDTO> event) {
        return exchange(tenant, REALTIME_REFUND, event);
    }

    @Override
    public ProjectionAckDTO applyRefundSummary(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, REALTIME_REFUND_SUMMARY, event);
    }

    private <Q, P extends Message, S extends Message> ProjectionAckDTO exchange(
            String tenant, RpcOperation<Q, ProjectionAckDTO, P, S> operation, Q event) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            var response = rpc.call(operation, event, token);
            if (response == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return response;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
