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

import io.github.loadup.acquire.app.model.payment.command.ParentPaymentFenceCommand;
import io.github.loadup.acquire.app.model.payment.command.PaymentCloseCommand;
import io.github.loadup.acquire.app.model.payment.command.PaymentExecuteCommand;
import io.github.loadup.acquire.app.model.payment.command.PaymentInputExpireCommand;
import io.github.loadup.acquire.app.model.payment.command.PaymentInputPrepareCommand;
import io.github.loadup.acquire.app.model.payment.dto.ParentFenceDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentCloseDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentInputDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentInputExpiryDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentInputLookupDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentOutcomeDTO;
import io.github.loadup.acquire.app.model.payment.dto.PaymentSettlementFactsDTO;
import io.github.loadup.acquire.app.model.payment.query.ParentFenceStatusQuery;
import io.github.loadup.acquire.app.model.payment.query.PaymentInputLookupQuery;
import io.github.loadup.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.acquire.app.port.PaymentClosureClient;
import io.github.loadup.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.acquire.client.dto.payment.PaymentExecutionDTO;
import io.github.loadup.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.acquire.infrastructure.converter.RemoteContractConverter;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.contracts.event.payment.OrderRefundSummaryEventDTO;
import io.github.loadup.payment.client.http.PaymentHttpService;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentAcceptanceHttpClient
        implements PaymentAcceptanceClient, PaymentOutcomeClient, PaymentClosureClient {
    private final RemoteContractConverter contracts;
    private final PaymentTransportProperties properties;
    private final ServiceAccessTokens tokens;
    private final PaymentHttpService paymentHttp;

    public PaymentAcceptanceHttpClient(
            PaymentHttpService paymentHttp,
            ServiceAccessTokens tokens,
            PaymentTransportProperties properties,
            RemoteContractConverter contracts) {
        this.contracts = contracts;
        this.paymentHttp = paymentHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public PaymentInputDTO prepareInput(PaymentInputPrepareCommand command) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentInputDTOSuccessResponse(
                        paymentHttp.inputPrepare(authorization, contracts.toRemote(command))),
                true);
    }

    @Override
    public PaymentInputLookupDTO lookupInput(PaymentInputLookupQuery query) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentInputLookupDTOSuccessResponse(
                        paymentHttp.inputLookup(authorization, contracts.toRemote(query))),
                true);
    }

    @Override
    public PaymentInputExpiryDTO expireInput(PaymentInputExpireCommand command) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentInputExpiryDTOSuccessResponse(
                        paymentHttp.inputExpire(authorization, contracts.toRemote(command))),
                true);
    }

    @Override
    public PaymentExecutionDTO accept(PaymentExecuteCommand command) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentExecutionDTOSuccessResponse(
                        paymentHttp.accept(authorization, contracts.toRemote(command))),
                true);
    }

    @Override
    public PaymentExecutionDTO detail(String paymentId) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentExecutionDTOSuccessResponse(
                        paymentHttp.detail(authorization, new IdQuery(paymentId))),
                false);
    }

    @Override
    public PaymentOutcomeDTO outcome(String paymentId) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentOutcomeDTOSuccessResponse(
                        paymentHttp.outcome(authorization, new IdQuery(paymentId))),
                false);
    }

    @Override
    public PaymentSettlementFactsDTO facts(String paymentId) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentSettlementFactsDTOSuccessResponse(
                        paymentHttp.facts(authorization, new IdQuery(paymentId))),
                false);
    }

    @Override
    public OrderRefundSummaryEventDTO refundSummary(String orderId) {
        return exchange(authorization -> paymentHttp.refundSummary(authorization, new IdQuery(orderId)), false);
    }

    @Override
    public ParentFenceDTO fenceParent(ParentPaymentFenceCommand command) {
        return exchange(
                authorization -> contracts.fromPaymentParentFenceDTOSuccessResponse(
                        paymentHttp.fenceParent(authorization, contracts.toRemote(command))),
                false);
    }

    @Override
    public PaymentCloseDTO close(PaymentCloseCommand command) {
        return exchange(
                authorization -> contracts.fromPaymentPaymentCloseDTOSuccessResponse(
                        paymentHttp.close(authorization, contracts.toRemote(command))),
                false);
    }

    @Override
    public ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query) {
        return exchange(
                authorization -> contracts.fromPaymentParentFenceDTOSuccessResponse(
                        paymentHttp.parentFenceStatus(authorization, contracts.toRemote(query))),
                false);
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
