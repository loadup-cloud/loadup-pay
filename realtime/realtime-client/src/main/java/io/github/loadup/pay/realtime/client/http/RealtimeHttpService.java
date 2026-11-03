/*
 * #%L
 * LoadUp Pay Realtime Client
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
package io.github.loadup.pay.realtime.client.http;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface RealtimeHttpService {
    String ORDER_PATH = "/api/internal/v1/realtime/projections/order";
    String PAYMENT_PATH = "/api/internal/v1/realtime/projections/payment";
    String REFUND_PATH = "/api/internal/v1/realtime/projections/refund";
    String REFUND_SUMMARY_PATH = "/api/internal/v1/realtime/projections/refund-summary";

    @PostExchange(ORDER_PATH)
    SuccessResponse<ProjectionAckDTO> order(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<OrderSnapshotEventDTO> request);

    @PostExchange(PAYMENT_PATH)
    SuccessResponse<ProjectionAckDTO> payment(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<PaymentSnapshotEventDTO> request);

    @PostExchange(REFUND_PATH)
    SuccessResponse<ProjectionAckDTO> refund(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<RefundSnapshotEventDTO> request);

    @PostExchange(REFUND_SUMMARY_PATH)
    SuccessResponse<ProjectionAckDTO> refundSummary(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<OrderRefundSummaryEventDTO> request);
}
