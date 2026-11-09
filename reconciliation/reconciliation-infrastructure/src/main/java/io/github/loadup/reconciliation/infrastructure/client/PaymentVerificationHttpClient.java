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
package io.github.loadup.reconciliation.infrastructure.client;

import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.payment.client.http.PaymentVerificationHttpService;
import io.github.loadup.reconciliation.app.model.payment.dto.PaymentVerificationDTO;
import io.github.loadup.reconciliation.app.model.payment.query.PaymentVerificationQuery;
import io.github.loadup.reconciliation.app.model.payment.support.PaymentEvidenceContracts;
import io.github.loadup.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.reconciliation.infrastructure.config.ReconciliationPaymentProperties;
import io.github.loadup.reconciliation.infrastructure.converter.RemoteContractConverter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentVerificationHttpClient implements ReconciliationPaymentVerification {
    private final RemoteContractConverter contracts;
    private final ReconciliationPaymentProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentVerificationHttpService paymentVerificationHttp;

    public PaymentVerificationHttpClient(
            PaymentVerificationHttpService paymentVerificationHttp,
            ReconciliationPaymentProperties properties,
            ServiceAccessTokens tokens,
            RemoteContractConverter contracts) {
        this.contracts = contracts;
        this.paymentVerificationHttp = paymentVerificationHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public PaymentVerificationDTO verify(PaymentVerificationQuery query) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var value = HttpServiceResponses.requiredData(
                    contracts.fromPaymentPaymentVerificationDTOSuccessResponse(paymentVerificationHttp.verify(
                            "Bearer " + tokens.token(properties.serviceId(), properties.audience()),
                            contracts.toRemote(query))));
            return PaymentEvidenceContracts.verification(value);
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
