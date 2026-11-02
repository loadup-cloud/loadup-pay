package io.github.loadup.pay.product.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface ProductHttpService {
    String AUTHORIZE_PATH = "/api/internal/v1/product/admissions/authorize";
    String DETAIL_PATH = "/api/internal/v1/product/admissions/detail";

    @PostExchange(AUTHORIZE_PATH)
    SuccessResponse<OrderAdmissionDTO> authorize(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody OrderAdmissionAuthorizeCommand command);

    @PostExchange(DETAIL_PATH)
    SuccessResponse<OrderAdmissionDTO> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery query);
}
