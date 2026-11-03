/*
 * #%L
 * LoadUp Pay Acquire Adapter
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
package io.github.loadup.pay.acquire.adapter.web;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.client.facade.FeeRefundOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.RefundNotificationFacade;
import io.github.loadup.pay.acquire.client.http.AcquireHttpService;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal events")
public class AcquireEventController {
    private final PaymentOutcomeFacade paymentOutcome;
    private final FeeRefundOutcomeFacade feeRefundOutcome;
    private final RefundNotificationFacade refundNotification;

    public AcquireEventController(
            PaymentOutcomeFacade paymentOutcome,
            FeeRefundOutcomeFacade feeRefundOutcome,
            RefundNotificationFacade refundNotification) {
        this.paymentOutcome = paymentOutcome;
        this.feeRefundOutcome = feeRefundOutcome;
        this.refundNotification = refundNotification;
    }

    @PostMapping(AcquireHttpService.PAYMENT_OUTCOME_PATH)
    @Operation(summary = "PaymentOutcome using verified caller identity")
    public SuccessResponse<PaymentOutcomeAckDTO> paymentOutcome(
            @Valid @RequestBody EventEnvelope<PaymentOutcomeEventDTO> request) {
        return SuccessResponse.of(paymentOutcome.accept(request));
    }

    @PostMapping(AcquireHttpService.FEE_REFUND_PATH)
    @Operation(summary = "FeeRefund using verified caller identity")
    public SuccessResponse<FeeRefundAckDTO> feeRefund(
            @Valid @RequestBody EventEnvelope<OrderRefundSummaryEventDTO> request) {
        return SuccessResponse.of(feeRefundOutcome.accept(request));
    }

    @PostMapping(AcquireHttpService.REFUND_NOTIFICATION_PATH)
    @Operation(summary = "RefundNotification using verified caller identity")
    public SuccessResponse<RefundNotificationAckDTO> refundNotification(
            @Valid @RequestBody EventEnvelope<RefundMerchantEventDTO> request) {
        return SuccessResponse.of(refundNotification.accept(request));
    }
}
