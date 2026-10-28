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
import io.github.loadup.pay.acquire.client.command.PaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentRecoveryDTO;
import io.github.loadup.pay.acquire.client.facade.PaymentRecoveryFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/acquire/payments")
@Tag(name = "Merchant original payment recovery")
public class PaymentRecoveryController {
    private final PaymentRecoveryFacade facade;

    public PaymentRecoveryController(PaymentRecoveryFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/recover")
    @Operation(summary = "Recover only the original merchant action after a lost response; never creates a replacement payment")
    public ResponseEntity<SuccessResponse<PaymentRecoveryDTO>> recover(@Valid @RequestBody PaymentRecoverCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.recover(command)));
    }
}
