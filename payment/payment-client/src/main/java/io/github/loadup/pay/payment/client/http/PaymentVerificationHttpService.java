package io.github.loadup.pay.payment.client.http;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface PaymentVerificationHttpService {
    String VERIFY_PATH = "/api/internal/v1/payment/verifications/verify";

    @PostExchange(VERIFY_PATH)
    SuccessResponse<PaymentVerificationDTO> verify(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentVerificationQuery request);
}
