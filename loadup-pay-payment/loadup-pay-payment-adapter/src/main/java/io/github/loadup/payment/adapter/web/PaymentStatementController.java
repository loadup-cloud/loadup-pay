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
import io.github.loadup.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.payment.client.facade.PaymentStatementFacade;
import io.github.loadup.payment.client.http.PaymentStatementHttpService;
import io.github.loadup.payment.client.query.PaymentStatementQuery;
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
