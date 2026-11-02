package io.github.loadup.pay.customer.client.http;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.customer.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantEligibilityDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The bearer token is supplied per invocation and must have the customer audience.
 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface CustomerHttpService {
    String AUTHORIZE_PATH = "/api/internal/v1/customer/eligibility/authorize";

    @PostExchange(AUTHORIZE_PATH)
    SuccessResponse<MerchantEligibilityDTO> authorize(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody MerchantActionAuthorizeCommand command);
}
