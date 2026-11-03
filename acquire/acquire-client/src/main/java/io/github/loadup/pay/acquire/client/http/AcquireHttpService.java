/*
 * #%L
 * LoadUp Pay Acquire Client
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
package io.github.loadup.pay.acquire.client.http;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface AcquireHttpService {
    String PAYMENT_OUTCOME_PATH = "/api/internal/v1/acquire/events/payment-outcome";
    String FEE_REFUND_PATH = "/api/internal/v1/acquire/events/fee-refund";
    String REFUND_NOTIFICATION_PATH = "/api/internal/v1/acquire/events/refund-notification";

    @PostExchange(PAYMENT_OUTCOME_PATH)
    SuccessResponse<PaymentOutcomeAckDTO> paymentOutcome(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<PaymentOutcomeEventDTO> request);

    @PostExchange(FEE_REFUND_PATH)
    SuccessResponse<FeeRefundAckDTO> feeRefund(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<OrderRefundSummaryEventDTO> request);

    @PostExchange(REFUND_NOTIFICATION_PATH)
    SuccessResponse<RefundNotificationAckDTO> refundNotification(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody EventEnvelope<RefundMerchantEventDTO> request);
}
