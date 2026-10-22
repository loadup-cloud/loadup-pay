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

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.facade.PaymentAttemptFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/executions")
@Tag(name = "Internal payment attempts")
public class PaymentAttemptController {
    private final PaymentAttemptFacade facade;
    public PaymentAttemptController(PaymentAttemptFacade facade) { this.facade = facade; }
    @PostMapping("/start-attempt")
    @Operation(summary = "Persist or replay one fixed channel attempt without asserting channel collection")
    public SuccessResponse<PaymentExecutionDTO> startAttempt(@Valid @RequestBody PaymentAttemptStartCommand command) {
        return SuccessResponse.of(facade.startAttempt(command));
    }
}
