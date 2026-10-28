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
import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationBatchDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationDifferenceDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationFacade;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationDifferenceQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation/batches")
@Tag(name = "Account scoped reconciliation operations")
public class ReconciliationController {
    private final ReconciliationFacade facade;

    public ReconciliationController(ReconciliationFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/create")
    @Operation(summary = "Create an idempotent batch for a trusted collecting account and statement date")
    public SuccessResponse<ReconciliationBatchDTO> create(@Valid @RequestBody ReconciliationBatchCreateCommand command) {
        return SuccessResponse.of(facade.create(command));
    }

    @PostMapping("/attach")
    @Operation(summary = "Attach an original immutable sealed input")
    public SuccessResponse<ReconciliationBatchDTO> attach(@Valid @RequestBody ReconciliationInputAttachCommand command) {
        return SuccessResponse.of(facade.attach(command));
    }

    @PostMapping("/start")
    @Operation(summary = "Atomically register the original run and durable comparison task")
    public SuccessResponse<ReconciliationBatchDTO> start(@Valid @RequestBody ReconciliationBatchStartCommand command) {
        return SuccessResponse.of(facade.start(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read the original inputs, execution and result summary")
    public SuccessResponse<ReconciliationBatchDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }

    @PostMapping("/differences")
    @Operation(summary = "Page stored differences without consulting the transaction service")
    public PageResponse<ReconciliationDifferenceDTO> differences(@Valid @RequestBody ReconciliationDifferenceQuery query) {
        return PageResponse.of(facade.differences(query));
    }
}
