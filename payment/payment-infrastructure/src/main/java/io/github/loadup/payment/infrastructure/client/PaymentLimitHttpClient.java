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
package io.github.loadup.payment.infrastructure.client;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.limitcenter.client.command.LimitConfirmCommand;
import io.github.loadup.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.limitcenter.client.command.LimitRefundRestoreCommand;
import io.github.loadup.limitcenter.client.command.LimitReleaseCommand;
import io.github.loadup.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.limitcenter.client.dto.LimitRestoreDTO;
import io.github.loadup.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.payment.app.port.PaymentLimitClient;
import io.github.loadup.payment.infrastructure.config.PaymentLimitProperties;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentLimitHttpClient implements PaymentLimitClient {
    private final PaymentLimitProperties properties;
    private final ServiceAccessTokens tokens;
    private final LimitCenterHttpService limitCenterHttp;

    public PaymentLimitHttpClient(
            LimitCenterHttpService limitCenterHttp, ServiceAccessTokens tokens, PaymentLimitProperties properties) {
        this.limitCenterHttp = limitCenterHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public LimitReservationDTO reserve(LimitReserveCommand command) {
        return exchange(authorization -> limitCenterHttp.reserve(authorization, command));
    }

    @Override
    public LimitExecutionPermitDTO begin(LimitBeginExecutionCommand command) {
        return exchange(authorization -> limitCenterHttp.begin(authorization, command));
    }

    @Override
    public LimitExecutionVerificationDTO verify(LimitExecutionVerifyCommand command) {
        return exchange(authorization -> limitCenterHttp.verify(authorization, command));
    }

    @Override
    public LimitReservationDTO confirm(LimitConfirmCommand command) {
        return exchange(authorization -> limitCenterHttp.confirm(authorization, command));
    }

    @Override
    public LimitReleaseDTO release(LimitReleaseCommand command) {
        return exchange(authorization -> limitCenterHttp.release(authorization, command));
    }

    @Override
    public LimitRestoreDTO restoreRefund(LimitRefundRestoreCommand command) {
        return exchange(authorization -> limitCenterHttp.restoreRefund(authorization, command));
    }

    private <R> R exchange(Function<String, SuccessResponse<R>> call) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            return HttpServiceResponses.requiredData(call.apply("Bearer " + credential()));
        } catch (RuntimeException failure) {
            var code =
                    switch (HttpServiceResponses.resultCode(failure)) {
                        case "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED;
                        case "LIMIT_RESERVATION_EXPIRED" -> LIMIT_RESERVATION_EXPIRED;
                        case "LIMIT_EXECUTION_FENCED" -> EXECUTION_FENCED;
                        case "LIMIT_IDEMPOTENCY_CONFLICT" -> IDEMPOTENCY_CONFLICT;
                        default -> DEPENDENCY_UNAVAILABLE;
                    };
            throw new CommonException(code);
        }
    }

    private String credential() {
        return tokens.token(properties.serviceId(), properties.audience());
    }
}
