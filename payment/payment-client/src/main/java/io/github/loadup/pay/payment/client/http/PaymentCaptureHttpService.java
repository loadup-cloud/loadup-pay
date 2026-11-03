/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
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
 */
package io.github.loadup.pay.payment.client.http;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json", contentType = "application/json")
public interface PaymentCaptureHttpService {
    String CREATE_PATH = "/api/internal/v1/payment/captures/create";
    String RETRY_PATH = "/api/internal/v1/payment/captures/retry";
    String DETAIL_PATH = "/api/internal/v1/payment/captures/detail";
    String LINES_PATH = "/api/internal/v1/payment/captures/lines";

    @PostExchange(CREATE_PATH)
    SuccessResponse<PaymentCaptureDTO> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentCaptureCreateCommand request);

    @PostExchange(RETRY_PATH)
    SuccessResponse<PaymentCaptureDTO> retry(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentCaptureRetryCommand request);

    @PostExchange(DETAIL_PATH)
    SuccessResponse<PaymentCaptureDTO> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, @RequestBody IdQuery request);

    @PostExchange(LINES_PATH)
    PaymentCapturePageResponse lines(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody PaymentCaptureLinesQuery request);
}
