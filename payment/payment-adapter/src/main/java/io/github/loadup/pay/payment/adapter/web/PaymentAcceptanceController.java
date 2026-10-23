/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.facade.PaymentAcceptanceFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/executions")
@Tag(name = "Payment acceptance")
public class PaymentAcceptanceController {
    private final PaymentAcceptanceFacade facade;

    public PaymentAcceptanceController(PaymentAcceptanceFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept or replay a fixed internal payment intent without submitting to a channel")
    public SuccessResponse<PaymentExecutionDTO> accept(@Valid @RequestBody PaymentExecuteCommand command) {
        return SuccessResponse.of(facade.accept(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read the authoritative payment within the original service scope")
    public SuccessResponse<PaymentExecutionDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }
}
