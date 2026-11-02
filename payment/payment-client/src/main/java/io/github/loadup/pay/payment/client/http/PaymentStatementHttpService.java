package io.github.loadup.pay.payment.client.http;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.payment.client.query.PaymentStatementQuery;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface PaymentStatementHttpService {
    String DOWNLOAD_PATH = "/api/internal/v1/payment/statements/download";

    @PostExchange(DOWNLOAD_PATH)
    SuccessResponse<PaymentStatementDTO> download(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody PaymentStatementQuery request);
}
