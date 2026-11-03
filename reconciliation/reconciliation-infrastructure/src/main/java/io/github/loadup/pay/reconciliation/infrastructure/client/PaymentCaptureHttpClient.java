/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
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
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.http.PaymentCaptureHttpService;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContracts;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentCaptures;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentCaptureHttpClient implements ReconciliationPaymentCaptures {
    private final ReconciliationPaymentProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentCaptureHttpService paymentCaptureHttp;

    public PaymentCaptureHttpClient(
            PaymentCaptureHttpService paymentCaptureHttp,
            ReconciliationPaymentProperties properties,
            ServiceAccessTokens tokens) {
        this.paymentCaptureHttp = paymentCaptureHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public PaymentCaptureDTO create(String tenant, PaymentCaptureCreateCommand command) {
        return call(
                tenant,
                authorization -> PaymentCaptureContracts.capture(
                        HttpServiceResponses.requiredData(paymentCaptureHttp.create(authorization, command))));
    }

    @Override
    public PaymentCaptureDTO retry(String tenant, PaymentCaptureRetryCommand command) {
        return call(
                tenant,
                authorization -> PaymentCaptureContracts.capture(
                        HttpServiceResponses.requiredData(paymentCaptureHttp.retry(authorization, command))));
    }

    @Override
    public PaymentCaptureDTO detail(String tenant, String captureId) {
        return call(
                tenant,
                authorization -> PaymentCaptureContracts.capture(HttpServiceResponses.requiredData(
                        paymentCaptureHttp.detail(authorization, new IdQuery(captureId)))));
    }

    @Override
    public PageDTO<PaymentLedgerLineDTO> lines(String tenant, PaymentCaptureLinesQuery query) {
        return call(tenant, authorization -> {
            var response = paymentCaptureHttp.lines(authorization, query);
            if (response == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            var data = HttpServiceResponses.requiredData(new SuccessResponse<>(response.result(), response.data()));
            return PaymentCaptureContracts.page(new PageDTO<>(data, response.pageInfo()), query);
        });
    }

    private <R> R call(String tenant, Function<String, R> request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!properties.tenantId().equals(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        try {
            var result = request.apply("Bearer " + tokens.token(tenant, properties.serviceId(), properties.audience()));
            if (result == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            return result;
        } catch (RuntimeException failure) {
            throw new CommonException(
                    switch (HttpServiceResponses.resultCode(failure)) {
                        case "PAYMENT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                        case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                        case "PAYMENT_NOT_FOUND" -> NOT_FOUND;
                        case "PAYMENT_INVALID_STATE" -> INPUT_NOT_SEALED;
                        default -> DEPENDENCY_UNAVAILABLE;
                    });
        }
    }
}
