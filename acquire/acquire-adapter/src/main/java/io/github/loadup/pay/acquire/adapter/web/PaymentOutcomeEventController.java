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
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.acquire.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquire/events")
@Tag(name = "Trusted acquiring event consumption")
public class PaymentOutcomeEventController {
    private final PaymentOutcomeFacade facade;
    public PaymentOutcomeEventController(PaymentOutcomeFacade facade) { this.facade = facade; }
    @PostMapping("/payment-outcome")
    @Operation(summary = "Verify payment authority and atomically consume the original allocation with a durable ACK")
    public SuccessResponse<PaymentOutcomeAckDTO> accept(@Valid @RequestBody PayEventEnvelope<PaymentOutcomeEventDTO> event) {
        return SuccessResponse.of(facade.accept(event));
    }
}
