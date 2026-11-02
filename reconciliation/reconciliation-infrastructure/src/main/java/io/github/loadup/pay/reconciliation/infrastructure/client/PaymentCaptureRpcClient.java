/*
 * #%L
 * LoadUp Pay Reconciliation infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.github.loadup.pay.reconciliation.infrastructure.client;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_NOT_SEALED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.components.rpc.dubbo.RpcExceptionCodes;
import io.github.loadup.components.rpc.dubbo.RpcTokenContext;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.rpc.PaymentCaptureRpcMapper;
import io.github.loadup.pay.payment.rpc.proto.PaymentCaptureRpc;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentCaptures;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import java.time.Clock;
import java.util.function.Supplier;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentCaptureRpcClient implements ReconciliationPaymentCaptures {
    private final ReconciliationPaymentProperties properties;
    private final ReconciliationServiceTokenReader tokens;
    private final PaymentCaptureRpcMapper paymentCaptureRpcMapper;

    @DubboReference
    private PaymentCaptureRpc paymentCaptureRpc;

    public PaymentCaptureRpcClient(
            PaymentCaptureRpcMapper paymentCaptureRpcMapper,
            ReconciliationPaymentProperties properties,
            JsonMapper mapper,
            Clock clock) {
        this.paymentCaptureRpcMapper = paymentCaptureRpcMapper;
        this.properties = properties;
        this.tokens = new ReconciliationServiceTokenReader(mapper, clock);
    }

    @Override
    public PaymentCaptureDTO create(String tenant, PaymentCaptureCreateCommand command) {
        return call(
                tenant,
                () -> paymentCaptureRpcMapper.fromWire(
                        paymentCaptureRpc.create(paymentCaptureRpcMapper.toWire(command))));
    }

    @Override
    public PaymentCaptureDTO retry(
            String tenant, io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand command) {
        return call(
                tenant,
                () -> paymentCaptureRpcMapper.fromWire(
                        paymentCaptureRpc.retry(paymentCaptureRpcMapper.toWire(command))));
    }

    @Override
    public PaymentCaptureDTO detail(String tenant, String captureId) {
        return call(
                tenant,
                () -> paymentCaptureRpcMapper.fromWire(
                        paymentCaptureRpc.detail(paymentCaptureRpcMapper.toWire(new IdQuery(captureId)))));
    }

    @Override
    public PageDTO<PaymentLedgerLineDTO> lines(String tenant, PaymentCaptureLinesQuery query) {
        return call(
                tenant,
                () -> paymentCaptureRpcMapper.fromWire(paymentCaptureRpc.lines(paymentCaptureRpcMapper.toWire(query))));
    }

    private <R> R call(String tenant, Supplier<R> request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!properties.tenantId().equals(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        try {
            var result = RpcTokenContext.call(
                    tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile()),
                    request);
            if (result == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return result;
        } catch (RuntimeException failure) {
            throw new CommonException(
                    switch (RpcExceptionCodes.resultCode(failure)) {
                        case "PAYMENT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                        case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                        case "PAYMENT_NOT_FOUND" -> NOT_FOUND;
                        case "PAYMENT_INVALID_STATE" -> INPUT_NOT_SEALED;
                        default -> DEPENDENCY_UNAVAILABLE;
                    });
        }
    }
}
