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
import io.github.loadup.pay.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PaymentRealtimeHttpClient implements PaymentRealtimeClient {
    private final PaymentRealtimeProperties properties;
    private final ServiceAccessTokens tokens;
    private final RealtimeHttpService realtimeHttp;

    public PaymentRealtimeHttpClient(
            RealtimeHttpService realtimeHttp, ServiceAccessTokens tokens, PaymentRealtimeProperties properties) {
        this.realtimeHttp = realtimeHttp;
        this.properties = properties;
        this.tokens = tokens;
    }

    @Override
    public boolean supportsTenant(String tenant) {
        return properties.tenantId().equals(tenant);
    }

    @Override
    public ProjectionAckDTO apply(String tenant, EventEnvelope<PaymentSnapshotEventDTO> event) {
        return exchange(tenant, authorization -> realtimeHttp.payment(authorization, event));
    }

    @Override
    public ProjectionAckDTO applyRefund(String tenant, EventEnvelope<RefundSnapshotEventDTO> event) {
        return exchange(tenant, authorization -> realtimeHttp.refund(authorization, event));
    }

    @Override
    public ProjectionAckDTO applyRefundSummary(String tenant, EventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, authorization -> realtimeHttp.refundSummary(authorization, event));
    }

    private ProjectionAckDTO exchange(String tenant, Function<String, SuccessResponse<ProjectionAckDTO>> call) {
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
