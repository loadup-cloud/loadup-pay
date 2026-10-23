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
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.infrastructure.config.PaymentRealtimeProperties;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Map;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

@Component
public class PaymentRealtimeHttpClient implements PaymentRealtimeClient {
    private final HttpTemplate http;
    private final JsonMapper mapper;
    private final PaymentRealtimeProperties properties;
    private final PaymentServiceTokenReader tokens;

    public PaymentRealtimeHttpClient(HttpTemplate http, JsonMapper mapper, PaymentRealtimeProperties properties,
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
    public ProjectionAckDTO apply(String tenant, PayEventEnvelope<PaymentSnapshotEventDTO> event) {
        return exchange(tenant, "payment", event);
    }

    @Override
    public ProjectionAckDTO applyRefund(String tenant, PayEventEnvelope<RefundSnapshotEventDTO> event) {
        return exchange(tenant, "refund", event);
    }

    @Override
    public ProjectionAckDTO applyRefundSummary(String tenant, PayEventEnvelope<OrderRefundSummaryEventDTO> event) {
        return exchange(tenant, "refund-summary", event);
    }

    private ProjectionAckDTO exchange(String tenant, String operation, PayEventEnvelope<?> event) {
        if (!supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        try {
            var token = tokens.read(tenant, properties.serviceId(), properties.audience(), properties.tokenFile());
            var response = http.exchange("realtime", operation, new HttpCall(Map.of(), Map.of(), Map.of("Authorization", "Bearer " + token), event));
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null)
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            var body = mapper.readValue(response.getBody(), new TypeReference<SuccessResponse<ProjectionAckDTO>>() {
            });
            if (body == null || body.result() == null || body.data() == null
                    || !CommonResultCodeEnum.SUCCESS.getCode().equals(body.result().getCode())
                    || !CommonResultCodeEnum.SUCCESS.getStatus().equals(body.result().getStatus()))
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            return body.data();
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
