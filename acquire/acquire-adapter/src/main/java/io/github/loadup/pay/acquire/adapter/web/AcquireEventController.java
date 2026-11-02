package io.github.loadup.pay.acquire.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.client.facade.FeeRefundOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.acquire.client.facade.RefundNotificationFacade;
import io.github.loadup.pay.acquire.client.http.AcquireHttpService;
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
    public SuccessResponse<PaymentOutcomeAckDTO> paymentOutcome(@Valid @RequestBody EventEnvelope<PaymentOutcomeEventDTO> request) {
        return SuccessResponse.of(paymentOutcome.accept(request));
    }

    @PostMapping(AcquireHttpService.FEE_REFUND_PATH)
    @Operation(summary = "FeeRefund using verified caller identity")
    public SuccessResponse<FeeRefundAckDTO> feeRefund(@Valid @RequestBody EventEnvelope<OrderRefundSummaryEventDTO> request) {
        return SuccessResponse.of(feeRefundOutcome.accept(request));
    }

    @PostMapping(AcquireHttpService.REFUND_NOTIFICATION_PATH)
    @Operation(summary = "RefundNotification using verified caller identity")
    public SuccessResponse<RefundNotificationAckDTO> refundNotification(@Valid @RequestBody EventEnvelope<RefundMerchantEventDTO> request) {
        return SuccessResponse.of(refundNotification.accept(request));
    }
}
