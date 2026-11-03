package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.facade.PaymentStatementFacade;
import io.github.loadup.pay.payment.client.http.PaymentStatementHttpService;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal statements")
public class PaymentStatementController {
    private final PaymentStatementFacade paymentStatement;

    public PaymentStatementController(PaymentStatementFacade paymentStatement) {
        this.paymentStatement = paymentStatement;
    }

    @PostMapping(PaymentStatementHttpService.DOWNLOAD_PATH)
    @Operation(summary = "Download with verified service identity")
    public SuccessResponse<PaymentStatementDTO> download(@Valid @RequestBody PaymentStatementQuery request) {
        return SuccessResponse.of(paymentStatement.download(request));
    }
}
