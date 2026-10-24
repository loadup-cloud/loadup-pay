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

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.command.CashierPaymentStartCommand;
import io.github.loadup.pay.acquire.client.command.CashierPaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.dto.CashierPaymentRecoveryDTO;
import io.github.loadup.pay.acquire.client.dto.CashierPaymentDTO;
import io.github.loadup.pay.acquire.client.facade.CashierFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashier/payments")
@Tag(name = "Checkout capability payments")
public class CashierPaymentController {
    private final CashierFacade facade;
    public CashierPaymentController(CashierFacade facade) { this.facade = facade; }
    @PostMapping("/start")
    @Operation(summary = "Reserve one manual amount using a fixed action key within the original checkout session")
    public ResponseEntity<SuccessResponse<CashierPaymentDTO>> start(@Valid @RequestBody CashierPaymentStartCommand command) {
        return response(facade.startPayment(command));
    }
    @PostMapping("/progress")
    @Operation(summary = "Read and converge only a payment created by this original checkout session; id is paymentId")
    public ResponseEntity<SuccessResponse<CashierPaymentDTO>> progress(@Valid @RequestBody IdQuery query) { return response(facade.paymentProgress(query)); }
    @PostMapping("/recover")
    @Operation(summary = "Resolve the original action after a lost response without reserving money or replacing payer input")
    public ResponseEntity<SuccessResponse<CashierPaymentRecoveryDTO>> recover(@Valid @RequestBody CashierPaymentRecoverCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .body(SuccessResponse.of(facade.recoverPayment(command)));
    }
    private ResponseEntity<SuccessResponse<CashierPaymentDTO>> response(CashierPaymentDTO value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").body(SuccessResponse.of(value));
    }
}
