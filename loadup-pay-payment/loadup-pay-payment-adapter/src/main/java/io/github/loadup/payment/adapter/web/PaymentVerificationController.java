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
package io.github.loadup.payment.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.payment.client.facade.PaymentVerificationFacade;
import io.github.loadup.payment.client.http.PaymentVerificationHttpService;
import io.github.loadup.payment.client.query.PaymentVerificationQuery;
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
