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

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_READY;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.http.PaymentStatementHttpService;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.github.loadup.pay.payment.client.support.PaymentEvidenceContracts;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentStatements;
import io.github.loadup.pay.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentStatementHttpClient implements ReconciliationPaymentStatements {
    private final ReconciliationPaymentProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentStatementHttpService paymentStatementHttp;

    public PaymentStatementHttpClient(
            PaymentStatementHttpService paymentStatementHttp,
            ReconciliationPaymentProperties properties,
            ServiceAccessTokens tokens) {
        this.paymentStatementHttp = paymentStatementHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public PaymentStatementDTO download(PaymentStatementQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var value = HttpServiceResponses.requiredData(paymentStatementHttp.download(
                    "Bearer " + tokens.token(properties.serviceId(), properties.audience()), query));
            return PaymentEvidenceContracts.statement(value);
        } catch (RuntimeException failure) {
            throw new CommonException(
                    switch (HttpServiceResponses.resultCode(failure)) {
                        case "PAYMENT_STATEMENT_NOT_READY" -> STATEMENT_NOT_READY;
                        case "PAYMENT_STATEMENT_NOT_FOUND" -> STATEMENT_NOT_FOUND;
                        case "PAYMENT_INVALID_ARGUMENT", "PAYMENT_UNSUPPORTED_CAPABILITY" -> INPUT_INVALID;
                        default -> DEPENDENCY_UNAVAILABLE;
                    });
        }
    }
}
