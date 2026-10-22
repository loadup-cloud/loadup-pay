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
package io.github.loadup.pay.acquire.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.command.DirectPayCommand;
import io.github.loadup.pay.acquire.client.dto.DirectPayDTO;
import io.github.loadup.pay.acquire.client.facade.DirectPaymentFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquire/payments")
@Tag(name = "Merchant direct payments")
public class DirectPaymentController {
    private final DirectPaymentFacade facade;
    public DirectPaymentController(DirectPaymentFacade facade) { this.facade = facade; }
    @PostMapping("/direct-pay")
    @Operation(summary = "Create or replay an order and initiate a fixed payment action without a cashier session")
    public SuccessResponse<DirectPayDTO> directPay(@Valid @RequestBody DirectPayCommand command) {
        return SuccessResponse.of(facade.directPay(command));
    }
}
