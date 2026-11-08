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
package io.github.loadup.acquire.infrastructure.client;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.PAYMENT_INPUT_EXPIRED;

import io.github.loadup.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.acquire.app.port.PaymentClosureClient;
import io.github.loadup.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.contracts.event.OrderRefundSummaryEventDTO;
import io.github.loadup.contracts.payment.ParentFenceDTO;
import io.github.loadup.contracts.payment.ParentFenceStatusQuery;
import io.github.loadup.contracts.payment.ParentPaymentFenceCommand;
import io.github.loadup.contracts.payment.PaymentCloseCommand;
import io.github.loadup.contracts.payment.PaymentCloseDTO;
import io.github.loadup.contracts.payment.PaymentExecuteCommand;
import io.github.loadup.contracts.payment.PaymentExecutionDTO;
import io.github.loadup.contracts.payment.PaymentInputDTO;
import io.github.loadup.contracts.payment.PaymentInputExpireCommand;
import io.github.loadup.contracts.payment.PaymentInputExpiryDTO;
import io.github.loadup.contracts.payment.PaymentInputLookupDTO;
import io.github.loadup.contracts.payment.PaymentInputLookupQuery;
import io.github.loadup.contracts.payment.PaymentInputPrepareCommand;
import io.github.loadup.contracts.payment.PaymentOutcomeDTO;
import io.github.loadup.contracts.payment.PaymentSettlementFactsDTO;
import io.github.loadup.payment.client.http.PaymentHttpService;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentAcceptanceHttpClient
        implements PaymentAcceptanceClient, PaymentOutcomeClient, PaymentClosureClient {
    private final PaymentTransportProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentHttpService paymentHttp;

    public PaymentAcceptanceHttpClient(
            PaymentHttpService paymentHttp, ServiceAccessTokens tokens, PaymentTransportProperties properties) {
        this.paymentHttp = paymentHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public PaymentInputDTO prepareInput(PaymentInputPrepareCommand command) {
        return exchange(authorization -> paymentHttp.inputPrepare(authorization, command), true);
    }

    @Override
    public PaymentInputLookupDTO lookupInput(PaymentInputLookupQuery query) {
        return exchange(authorization -> paymentHttp.inputLookup(authorization, query), true);
    }

    @Override
    public PaymentInputExpiryDTO expireInput(PaymentInputExpireCommand command) {
        return exchange(authorization -> paymentHttp.inputExpire(authorization, command), true);
    }

    @Override
    public PaymentExecutionDTO accept(PaymentExecuteCommand command) {
        return exchange(authorization -> paymentHttp.accept(authorization, command), true);
    }

    @Override
    public PaymentExecutionDTO detail(String paymentId) {
        return exchange(authorization -> paymentHttp.detail(authorization, new IdQuery(paymentId)), false);
    }

    @Override
    public PaymentOutcomeDTO outcome(String paymentId) {
        return exchange(authorization -> paymentHttp.outcome(authorization, new IdQuery(paymentId)), false);
    }

    @Override
    public PaymentSettlementFactsDTO facts(String paymentId) {
        return exchange(authorization -> paymentHttp.facts(authorization, new IdQuery(paymentId)), false);
    }

    @Override
    public OrderRefundSummaryEventDTO refundSummary(String orderId) {
        return exchange(authorization -> paymentHttp.refundSummary(authorization, new IdQuery(orderId)), false);
    }

    @Override
    public ParentFenceDTO fenceParent(ParentPaymentFenceCommand command) {
        return exchange(authorization -> paymentHttp.fenceParent(authorization, command), false);
    }

    @Override
    public PaymentCloseDTO close(PaymentCloseCommand command) {
        return exchange(authorization -> paymentHttp.close(authorization, command), false);
    }

    @Override
    public ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query) {
        return exchange(authorization -> paymentHttp.parentFenceStatus(authorization, query), false);
    }

    private <R> R exchange(Function<String, SuccessResponse<R>> call, boolean guarded) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            return HttpServiceResponses.requiredData(call.apply("Bearer " + credential()));
        } catch (RuntimeException failure) {
            if (guarded) {
                var code =
                        switch (HttpServiceResponses.resultCode(failure)) {
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

    private String credential() {
        return tokens.token(properties.serviceId(), properties.audience());
    }
}
