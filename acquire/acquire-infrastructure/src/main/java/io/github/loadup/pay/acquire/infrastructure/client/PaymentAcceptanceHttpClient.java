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
package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentClosureClient;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum;
import io.github.loadup.pay.acquire.infrastructure.config.PaymentTransportProperties;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.PAYMENT_INPUT_EXPIRED;

@Component
public class PaymentAcceptanceHttpClient implements PaymentAcceptanceClient, PaymentOutcomeClient, PaymentClosureClient {
    private final HttpTemplate http;
    private final JsonMapper mapper;
    private final PaymentTransportProperties properties;
    private final AcquireServiceTokenReader tokens;

    public PaymentAcceptanceHttpClient(
            HttpTemplate http,
            JsonMapper mapper,
            PaymentTransportProperties properties,
            @Qualifier("acquireClock") Clock clock) {
        this.http = http;
        this.mapper = mapper;
        this.properties = properties;
        this.tokens = new AcquireServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentInputDTO prepareInput(String tenant, PaymentInputPrepareCommand command) {
        return exchange(tenant, "input-prepare", command, new TypeReference<SuccessResponse<PaymentInputDTO>>() {
        });
    }

    @Override
    public PaymentExecutionDTO accept(String tenant, PaymentExecuteCommand command) {
        return exchange(tenant, "accept", command, new TypeReference<SuccessResponse<PaymentExecutionDTO>>() {
        });
    }

    @Override
    public PaymentExecutionDTO detail(String tenant, String paymentId) {
        return exchange(tenant, "detail", new IdQuery(paymentId), new TypeReference<SuccessResponse<PaymentExecutionDTO>>() {
        });
    }

    @Override
    public PaymentOutcomeDTO outcome(String tenant, String paymentId) {
        return exchange(tenant, "outcome", new IdQuery(paymentId), new TypeReference<SuccessResponse<PaymentOutcomeDTO>>() {
        });
    }

    @Override
    public PaymentSettlementFactsDTO facts(String tenant, String paymentId) {
        return exchange(tenant, "facts", new IdQuery(paymentId), new TypeReference<SuccessResponse<PaymentSettlementFactsDTO>>() {
        });
    }

    @Override
    public ParentFenceDTO fenceParent(String tenant, ParentPaymentFenceCommand command) {
        return exchange(tenant, "fence-parent", command, new TypeReference<SuccessResponse<ParentFenceDTO>>() {
        });
    }

    @Override
    public PaymentCloseDTO close(String tenant, PaymentCloseCommand command) {
        return exchange(tenant, "close", command, new TypeReference<SuccessResponse<PaymentCloseDTO>>() {
        });
    }

    @Override
    public ParentFenceDTO parentFenceStatus(String tenant, ParentFenceStatusQuery query) {
        return exchange(tenant, "parent-fence-status", query, new TypeReference<SuccessResponse<ParentFenceDTO>>() {
        });
    }

    private <T> T exchange(String tenant, String operation, Object command, TypeReference<SuccessResponse<T>> responseType) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var response = http.exchange(
                    "payment",
                    operation,
                    new HttpCall(Map.of(), Map.of(), Map.of("Authorization", "Bearer " + credential(tenant)), command));
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            SuccessResponse<T> body = mapper.readValue(response.getBody(), responseType);
            if (body == null || body.result() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            if (!CommonResultCodeEnum.SUCCESS.getCode().equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS.getStatus().equals(body.result().getStatus())) {
                // Propagate only declared input business errors. Remote messages and arbitrary codes stay private.
                if ("input-prepare".equals(operation) || "accept".equals(operation)) {
                    var code = switch (body.result().getCode()) {
                        case "PAYMENT_IDEMPOTENCY_CONFLICT", "PAYMENT_INPUT_ALREADY_BOUND" -> IDEMPOTENCY_CONFLICT;
                        case "PAYMENT_INPUT_EXPIRED" -> PAYMENT_INPUT_EXPIRED;
                        case "PAYMENT_INVALID_ARGUMENT" -> INVALID_ARGUMENT;
                        default -> DEPENDENCY_UNAVAILABLE;
                    };
                    throw new CommonException(code);
                }
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            if (body.data() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return body.data();
        } catch (CommonException failure) {
            if (failure.getResultCode() instanceof AcquireResultCodeEnum) throw failure;
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential(String tenant) {
        return tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
    }
}
