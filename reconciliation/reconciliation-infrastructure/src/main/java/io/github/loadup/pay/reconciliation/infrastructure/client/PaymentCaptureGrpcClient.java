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
package io.github.loadup.pay.reconciliation.infrastructure.client;

import com.google.protobuf.Message;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.components.rpc.grpc.*;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcOperations;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentCaptures;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

@Component
public class PaymentCaptureGrpcClient implements ReconciliationPaymentCaptures {
    private final GrpcTransport transport;
    private final PaymentCaptureRpcOperations operations;
    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;
    public PaymentCaptureGrpcClient(GrpcTransport transport, PaymentCaptureRpcOperations operations, ReconciliationPaymentProperties properties,
            JsonMapper mapper, @Qualifier("reconciliationClock") Clock clock) {
        this.transport = transport; this.operations = operations; this.properties = properties; this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
    }
    @Override public PaymentCaptureDTO create(String tenant, PaymentCaptureCreateCommand command) { return call(tenant, operations.create(), command); }
    @Override public PaymentCaptureDTO retry(String tenant, io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand command) { return call(tenant, operations.retry(), command); }
    @Override public PaymentCaptureDTO detail(String tenant, String captureId) { return call(tenant, operations.detail(), new IdQuery(captureId)); }
    @Override public PageDTO<PaymentLedgerLineDTO> lines(String tenant, PaymentCaptureLinesQuery query) { return call(tenant, operations.lines(), query); }
    private <Q, R, P extends Message, S extends Message> R call(String tenant, RpcOperation<Q, R, P, S> operation, Q request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!properties.tenantId().equals(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var result = transport.call(operation, request, tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()));
            if (result == null) throw new CommonException(DEPENDENCY_UNAVAILABLE); return result;
        } catch (RuntimeException failure) {
            throw new CommonException(switch (RpcServerSupport.resultCode(failure)) {
                case "PAYMENT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                case "PAYMENT_NOT_FOUND" -> NOT_FOUND;
                case "PAYMENT_INVALID_STATE" -> INPUT_NOT_SEALED;
                default -> DEPENDENCY_UNAVAILABLE;
            });
        }
    }
}
