package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.facade.PaymentVerificationFacade;
import io.github.loadup.pay.payment.client.http.PaymentVerificationHttpService;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal verifications")
public class PaymentVerificationController {
    private final PaymentVerificationFacade paymentVerification;

    public PaymentVerificationController(PaymentVerificationFacade paymentVerification) {
        this.paymentVerification = paymentVerification;
    }

    @PostMapping(PaymentVerificationHttpService.VERIFY_PATH)
    @Operation(summary = "Verify with verified service identity")
    public SuccessResponse<PaymentVerificationDTO> verify(@Valid @RequestBody PaymentVerificationQuery request) {
        return SuccessResponse.of(paymentVerification.verify(request));
    }
}
