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
import io.github.loadup.limitcenter.client.http.LimitCenterHttpService;
import io.github.loadup.payment.app.model.limitcenter.command.LimitBeginExecutionCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitConfirmCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitExecutionVerifyCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitRefundRestoreCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReleaseCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReserveCommand;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionPermitDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitExecutionVerificationDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitRestoreDTO;
import io.github.loadup.payment.app.port.PaymentLimitClient;
import io.github.loadup.payment.infrastructure.config.PaymentLimitProperties;
import io.github.loadup.payment.infrastructure.converter.RemoteContractConverter;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentLimitHttpClient implements PaymentLimitClient {
    private final RemoteContractConverter contracts;
    private final PaymentLimitProperties properties;
    private final ServiceAccessTokens tokens;
    private final LimitCenterHttpService limitCenterHttp;

    public PaymentLimitHttpClient(
            LimitCenterHttpService limitCenterHttp,
            ServiceAccessTokens tokens,
            PaymentLimitProperties properties,
            RemoteContractConverter contracts) {
        this.contracts = contracts;
        this.limitCenterHttp = limitCenterHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public LimitReservationDTO reserve(LimitReserveCommand command) {
        return exchange(authorization -> contracts.fromLimitcenterLimitReservationDTOSuccessResponse(
                limitCenterHttp.reserve(authorization, contracts.toRemote(command))));
    }

    @Override
    public LimitExecutionPermitDTO begin(LimitBeginExecutionCommand command) {
        return exchange(authorization -> contracts.fromLimitcenterLimitExecutionPermitDTOSuccessResponse(
                limitCenterHttp.begin(authorization, contracts.toRemote(command))));
    }

    @Override
    public LimitExecutionVerificationDTO verify(LimitExecutionVerifyCommand command) {
        return exchange(authorization -> contracts.fromLimitcenterLimitExecutionVerificationDTOSuccessResponse(
                limitCenterHttp.verify(authorization, contracts.toRemote(command))));
    }

    @Override
    public LimitReservationDTO confirm(LimitConfirmCommand command) {
        return exchange(authorization -> contracts.fromLimitcenterLimitReservationDTOSuccessResponse(
                limitCenterHttp.confirm(authorization, contracts.toRemote(command))));
    }

    @Override
    public LimitReleaseDTO release(LimitReleaseCommand command) {
        return exchange(authorization -> contracts.fromLimitcenterLimitReleaseDTOSuccessResponse(
                limitCenterHttp.release(authorization, contracts.toRemote(command))));
    }

    @Override
    public LimitRestoreDTO restoreRefund(LimitRefundRestoreCommand command) {
        return exchange(authorization -> contracts.fromLimitcenterLimitRestoreDTOSuccessResponse(
                limitCenterHttp.restoreRefund(authorization, contracts.toRemote(command))));
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
