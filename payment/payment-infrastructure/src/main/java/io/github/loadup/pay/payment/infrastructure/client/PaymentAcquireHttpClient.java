/*
 * #%L
 * LoadUp Pay Payment Infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.payment.infrastructure.client;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.http.HttpServiceResponses;
import io.github.loadup.components.serviceauth.ServiceAccessTokens;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.client.http.AcquireHttpService;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentAcquireProperties;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentAcquireHttpClient implements PaymentAcquireClient {
    private final PaymentAcquireProperties properties;
    private final ServiceAccessTokens tokens;
    private final AcquireHttpService acquireHttp;

    public PaymentAcquireHttpClient(
            AcquireHttpService acquireHttp, ServiceAccessTokens tokens, PaymentAcquireProperties properties) {
        this.acquireHttp = acquireHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public PaymentOutcomeAckDTO accept(String tenant, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return exchange(tenant, authorization -> acquireHttp.paymentOutcome(authorization, event));
    }

    @Override
    public FeeRefundAckDTO acceptFeeRefund(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, authorization -> acquireHttp.feeRefund(authorization, event));
    }

    @Override
    public RefundNotificationAckDTO acceptRefundNotification(
            String tenant, EventEnvelope<RefundMerchantEventDTO> event) {
        return exchange(tenant, authorization -> acquireHttp.refundNotification(authorization, event));
    }

    private <R> R exchange(String tenant, Function<String, SuccessResponse<R>> call) {
        if (!supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        try {
            var token = tokens.token(tenant, properties.serviceId(), properties.audience());
            return HttpServiceResponses.requiredData(call.apply("Bearer " + token));
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
