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

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementSubmitCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationStatementDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationStatementFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation/statements")
@Tag(name = "Official channel statement sources")
public class ReconciliationStatementController {
    private final ReconciliationStatementFacade facade;

    public ReconciliationStatementController(ReconciliationStatementFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/submit")
    @Operation(summary = "Persist an original official source request and native download task")
    public SuccessResponse<ReconciliationStatementDTO> submit(@Valid @RequestBody ReconciliationStatementSubmitCommand command) {
        return SuccessResponse.of(facade.submit(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read source and native download task state without downloading again")
    public SuccessResponse<ReconciliationStatementDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }

    @PostMapping("/retry")
    @Operation(summary = "Retry only the expected original failed native source task")
    public SuccessResponse<ReconciliationStatementDTO> retry(@Valid @RequestBody ReconciliationStatementRetryCommand command) {
        return SuccessResponse.of(facade.retry(command));
    }
}
