/*
 * #%L
 * LoadUp Pay Reconciliation adapter
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.github.loadup.pay.reconciliation.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchTaskDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationFacade;
import io.github.loadup.pay.reconciliation.client.http.ReconciliationHttpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal batches")
public class ReconciliationInternalController {
    private final ReconciliationFacade reconciliation;

    public ReconciliationInternalController(ReconciliationFacade reconciliation) {
        this.reconciliation = reconciliation;
    }

    @PostMapping(ReconciliationHttpService.CREATE_PATH)
    @Operation(summary = "Create using verified caller identity")
    public SuccessResponse<ReconciliationBatchDTO> create(
            @Valid @RequestBody ReconciliationBatchCreateCommand request) {
        return SuccessResponse.of(reconciliation.create(request));
    }

    @PostMapping(ReconciliationHttpService.ATTACH_PATH)
    @Operation(summary = "Attach using verified caller identity")
    public SuccessResponse<ReconciliationBatchDTO> attach(
            @Valid @RequestBody ReconciliationInputAttachCommand request) {
        return SuccessResponse.of(reconciliation.attach(request));
    }

    @PostMapping(ReconciliationHttpService.START_PATH)
    @Operation(summary = "Start using verified caller identity")
    public SuccessResponse<ReconciliationBatchDTO> start(@Valid @RequestBody ReconciliationBatchStartCommand request) {
        return SuccessResponse.of(reconciliation.start(request));
    }

    @PostMapping(ReconciliationHttpService.RETRY_PATH)
    @Operation(summary = "Retry using verified caller identity")
    public SuccessResponse<ReconciliationBatchDTO> retry(@Valid @RequestBody ReconciliationBatchRetryCommand request) {
        return SuccessResponse.of(reconciliation.retry(request));
    }

    @PostMapping(ReconciliationHttpService.TASK_PATH)
    @Operation(summary = "Task using verified caller identity")
    public SuccessResponse<ReconciliationBatchTaskDTO> task(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(reconciliation.task(request));
    }

    @PostMapping(ReconciliationHttpService.DETAIL_PATH)
    @Operation(summary = "Detail using verified caller identity")
    public SuccessResponse<ReconciliationBatchDTO> detail(@Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(reconciliation.detail(request));
    }
}
