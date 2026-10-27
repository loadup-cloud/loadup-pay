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
package io.github.loadup.pay.reconciliation.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.*;
import io.github.loadup.pay.reconciliation.client.dto.*;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationLedgerFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reconciliation/ledger")
@Tag(name = "Authoritative payment reconciliation source")
public class ReconciliationLedgerController {
    private final ReconciliationLedgerFacade facade;
    public ReconciliationLedgerController(ReconciliationLedgerFacade facade) { this.facade = facade; }
    @PostMapping("/prepare") @Operation(summary = "Prepare or inspect the original source-owned asynchronous capture")
    public SuccessResponse<ReconciliationLedgerSourceDTO> prepare(@Valid @RequestBody ReconciliationLedgerPrepareCommand command) {
        return SuccessResponse.of(facade.prepare(command));
    }
    @PostMapping("/retry") @Operation(summary = "Retry only the expected original failed capture task without replacing a sealed source")
    public SuccessResponse<ReconciliationLedgerSourceDTO> retry(@Valid @RequestBody ReconciliationLedgerRetryCommand command) {
        return SuccessResponse.of(facade.retry(command));
    }
    @PostMapping("/import") @Operation(summary = "Fetch a sealed original capture and atomically attach its complete immutable ledger input")
    public SuccessResponse<ReconciliationBatchDTO> importSealed(@Valid @RequestBody ReconciliationLedgerImportCommand command) {
        return SuccessResponse.of(facade.importSealed(command));
    }
}
