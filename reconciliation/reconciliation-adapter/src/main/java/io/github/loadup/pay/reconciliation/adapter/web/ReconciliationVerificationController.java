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
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationSubmitCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationVerificationDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationVerificationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation/verifications")
@Tag(name = "Approved read-only source verification")
public class ReconciliationVerificationController {
    private final ReconciliationVerificationFacade facade;

    public ReconciliationVerificationController(ReconciliationVerificationFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/submit")
    @Operation(summary = "Persist an original approved review consultation and Outbox intent")
    public SuccessResponse<ReconciliationVerificationDTO> submit(@Valid @RequestBody ReconciliationVerificationSubmitCommand command) {
        return SuccessResponse.of(facade.submit(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read the recorded source snapshot and independent delivery state")
    public SuccessResponse<ReconciliationVerificationDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }

    @PostMapping("/retry")
    @Operation(summary = "Replay only the expected original failed delivery with an audit reason")
    public SuccessResponse<ReconciliationVerificationDTO> retry(@Valid @RequestBody ReconciliationVerificationRetryCommand command) {
        return SuccessResponse.of(facade.retry(command));
    }
}
