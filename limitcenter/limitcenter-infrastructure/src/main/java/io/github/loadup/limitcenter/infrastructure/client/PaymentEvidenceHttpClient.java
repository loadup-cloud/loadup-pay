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
package io.github.loadup.limitcenter.infrastructure.client;

import static io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.limitcenter.app.port.PaymentEvidenceClient;
import io.github.loadup.limitcenter.infrastructure.config.PaymentEvidenceProperties;
import io.github.loadup.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.payment.client.http.PaymentHttpService;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentEvidenceHttpClient implements PaymentEvidenceClient {
    private final ServiceAccessTokens tokens;
    private final PaymentEvidenceProperties properties;
    private final PaymentHttpService paymentHttp;

    public PaymentEvidenceHttpClient(
            PaymentHttpService paymentHttp, ServiceAccessTokens tokens, PaymentEvidenceProperties properties) {
        this.paymentHttp = paymentHttp;
        this.tokens = tokens;
        this.properties = properties;
    }

    @Override
    public PaymentSettlementFactsDTO paymentFacts(String paymentId) {
        return exchange(authorization -> paymentHttp.facts(authorization, new IdQuery(paymentId)));
    }

    @Override
    public RefundSettlementFactsDTO refundFacts(String refundId) {
        return exchange(authorization -> paymentHttp.refundFacts(authorization, new IdQuery(refundId)));
    }

    private <R> R exchange(Function<String, SuccessResponse<R>> call) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            return HttpServiceResponses.requiredData(call.apply("Bearer " + credential()));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential() {
        return tokens.token(properties.serviceId(), properties.audience());
    }
}
