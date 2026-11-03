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
package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.facade.PaymentCaptureFacade;
import io.github.loadup.pay.payment.client.http.PaymentCaptureHttpService;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal captures")
public class PaymentCaptureController {
    private final PaymentCaptureFacade paymentCapture;

    public PaymentCaptureController(PaymentCaptureFacade paymentCapture) {
        this.paymentCapture = paymentCapture;
    }

    @PostMapping(PaymentCaptureHttpService.CREATE_PATH)
    @Operation(summary = "Create with verified service identity")
    public SuccessResponse<PaymentCaptureDTO> create(@Valid @RequestBody PaymentCaptureCreateCommand request) {
        return SuccessResponse.of(paymentCapture.create(request));
    }

    @PostMapping(PaymentCaptureHttpService.RETRY_PATH)
    @Operation(summary = "Retry with verified service identity")
    public SuccessResponse<PaymentCaptureDTO> retry(@Valid @RequestBody PaymentCaptureRetryCommand request) {
        return SuccessResponse.of(paymentCapture.retry(request));
    }

    @PostMapping(PaymentCaptureHttpService.DETAIL_PATH)
    @Operation(summary = "Detail with verified service identity")
    public SuccessResponse<PaymentCaptureDTO> detail(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(paymentCapture.detail(request));
    }

    @PostMapping(PaymentCaptureHttpService.LINES_PATH)
    @Operation(summary = "Lines with verified service identity")
    public PageResponse<PaymentLedgerLineDTO> lines(@Valid @RequestBody PaymentCaptureLinesQuery request) {
        return PageResponse.of(paymentCapture.lines(request));
    }
}
