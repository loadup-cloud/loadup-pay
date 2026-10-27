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
import io.github.loadup.pay.payment.client.dto.PaymentUnknownRecoveryPageDTO;
import io.github.loadup.pay.payment.client.facade.PaymentUnknownRecoveryFacade;
import io.github.loadup.pay.payment.client.query.PaymentUnknownRecoveryQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/operations/recovery/unknown")
@Tag(name = "Payment UNKNOWN investigations")
public class PaymentUnknownRecoveryController {
    private final PaymentUnknownRecoveryFacade facade;
    public PaymentUnknownRecoveryController(PaymentUnknownRecoveryFacade facade) { this.facade = facade; }
    @PostMapping("/list")
    @Operation(summary = "List tenant-scoped UNKNOWN payment/child-refund cases; operations SERVICE with recovery read permission only")
    public ResponseEntity<SuccessResponse<PaymentUnknownRecoveryPageDTO>> list(@Valid @RequestBody PaymentUnknownRecoveryQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.list(query)));
    }
}
