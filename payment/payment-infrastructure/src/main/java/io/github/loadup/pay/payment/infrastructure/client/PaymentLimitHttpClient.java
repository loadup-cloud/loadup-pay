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
package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.pay.payment.infrastructure.config.PaymentLimitProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;

@Component
public class PaymentLimitHttpClient implements PaymentLimitClient {
    private final HttpTemplate http;
    private final JsonMapper mapper;
    private final PaymentLimitProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentLimitHttpClient(HttpTemplate http, JsonMapper mapper, PaymentLimitProperties properties,
                                  @Qualifier("paymentClock") Clock clock) {
        this.http = http;
        this.mapper = mapper;
        this.properties = properties;
        this.tokens = new PaymentServiceTokenReader(mapper, clock);
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public LimitReservationDTO reserve(String tenant, LimitReserveCommand command) {
        return exchange(tenant, "reserve", command, new TypeReference<SuccessResponse<LimitReservationDTO>>() {
        });
    }

    @Override
    public LimitExecutionPermitDTO begin(String tenant, LimitBeginExecutionCommand command) {
        return exchange(tenant, "begin", command, new TypeReference<SuccessResponse<LimitExecutionPermitDTO>>() {
        });
    }

    @Override
    public LimitExecutionVerificationDTO verify(String tenant, LimitExecutionVerifyCommand command) {
        return exchange(tenant, "verify", command, new TypeReference<SuccessResponse<LimitExecutionVerificationDTO>>() {
        });
    }

    @Override
    public LimitReservationDTO confirm(String tenant, LimitConfirmCommand command) {
        return exchange(tenant, "confirm", command, new TypeReference<SuccessResponse<LimitReservationDTO>>() {
        });
    }

    @Override
    public LimitReleaseDTO release(String tenant, LimitReleaseCommand command) {
        return exchange(tenant, "release", command, new TypeReference<SuccessResponse<LimitReleaseDTO>>() {
        });
    }

    private <T> T exchange(String tenant, String operation, Object command, TypeReference<SuccessResponse<T>> responseType) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var response = http.exchange(
                    "limitcenter",
                    operation,
                    new HttpCall(Map.of(), Map.of(), Map.of("Authorization", "Bearer " + credential(tenant)), command));
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            SuccessResponse<T> body = mapper.readValue(response.getBody(), responseType);
            if (body == null || body.result() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            if (!CommonResultCodeEnum.SUCCESS.getCode().equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS.getStatus().equals(body.result().getStatus())) {
                var code = switch (body.result().getCode()) {
                    case "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED;
                    case "LIMIT_RESERVATION_EXPIRED" -> LIMIT_RESERVATION_EXPIRED;
                    case "LIMIT_EXECUTION_FENCED" -> EXECUTION_FENCED;
                    case "LIMIT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                    default -> DEPENDENCY_UNAVAILABLE;
                };
                throw new CommonException(code);
            }
            if (body.data() == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return body.data();
        } catch (CommonException failure) {
            if (failure.getResultCode() instanceof PaymentResultCodeEnum) throw failure;
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private String credential(String tenant) {
        return tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
    }

}
