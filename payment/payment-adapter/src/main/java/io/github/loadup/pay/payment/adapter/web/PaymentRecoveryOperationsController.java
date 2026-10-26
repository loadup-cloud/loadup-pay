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
import io.github.loadup.pay.payment.client.command.PaymentRecoveryRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentRecoveryRetryDTO;
import io.github.loadup.pay.payment.client.dto.PaymentRecoveryTaskDTO;
import io.github.loadup.pay.payment.client.facade.PaymentRecoveryOperationsFacade;
import io.github.loadup.pay.payment.client.query.PaymentRecoveryTaskQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/operations/recovery/tasks")
@Tag(name = "Audited payment recovery operations")
public class PaymentRecoveryOperationsController {
    private final PaymentRecoveryOperationsFacade facade;
    public PaymentRecoveryOperationsController(PaymentRecoveryOperationsFacade facade) { this.facade = facade; }
    @PostMapping("/detail")
    @Operation(summary = "Inspect one original payment native task; requires the configured operations SERVICE and recovery read permission")
    public ResponseEntity<SuccessResponse<PaymentRecoveryTaskDTO>> detail(@Valid @RequestBody PaymentRecoveryTaskQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.detail(query)));
    }
    @PostMapping("/retry")
    @Operation(summary = "Audit and retry a current FAILED task with exact observed preconditions; never changes a financial status")
    public ResponseEntity<SuccessResponse<PaymentRecoveryRetryDTO>> retry(@Valid @RequestBody PaymentRecoveryRetryCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.retry(command)));
    }
}
