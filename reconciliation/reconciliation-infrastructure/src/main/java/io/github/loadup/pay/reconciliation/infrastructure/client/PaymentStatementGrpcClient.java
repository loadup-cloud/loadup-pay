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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.rpc.grpc.GrpcTransport;
import io.github.loadup.components.rpc.grpc.RpcServerSupport;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.rpc.PaymentStatementRpcOperations;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentStatements;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_READY;

@Component
public class PaymentStatementGrpcClient implements ReconciliationPaymentStatements {
    private final GrpcTransport transport;
    private final PaymentStatementRpcOperations operations;
    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;

    public PaymentStatementGrpcClient(GrpcTransport transport, PaymentStatementRpcOperations operations, ReconciliationPaymentProperties properties,
                                      JsonMapper mapper, @Qualifier("reconciliationClock") Clock clock) {
        this.transport = transport;
        this.operations = operations;
        this.properties = properties;
        this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
    }

    @Override
    public PaymentStatementDTO download(String tenant, PaymentStatementQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!properties.tenantId().equals(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        try {
            var value = transport.call(operations.download(), query, tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()));
            if (value == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(switch (RpcServerSupport.resultCode(failure)) {
                case "PAYMENT_STATEMENT_NOT_READY" -> STATEMENT_NOT_READY;
                case "PAYMENT_STATEMENT_NOT_FOUND" -> STATEMENT_NOT_FOUND;
                case "PAYMENT_INVALID_ARGUMENT", "PAYMENT_UNSUPPORTED_CAPABILITY" -> INPUT_INVALID;
                default -> DEPENDENCY_UNAVAILABLE;
            });
        }
    }
}
