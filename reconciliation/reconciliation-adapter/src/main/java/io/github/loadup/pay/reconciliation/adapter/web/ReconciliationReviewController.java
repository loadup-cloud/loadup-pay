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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewDecideCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewEventDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationSourceFileDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationReviewFacade;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewHistoryQuery;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;

@RestController
@RequestMapping("/api/reconciliation/reviews")
@Tag(name = "Independent administrative difference review")
public class ReconciliationReviewController {
    private final ReconciliationReviewFacade facade;

    public ReconciliationReviewController(ReconciliationReviewFacade facade) {
        this.facade = facade;
    }

    @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload private review evidence without asserting channel provenance")
    public SuccessResponse<ReconciliationSourceFileDTO> evidence(@RequestParam("file") MultipartFile file) {
        try (var content = file.getInputStream()) {
            return SuccessResponse.of(facade.uploadEvidence(file.getOriginalFilename(), file.getContentType(), file.getSize(), content));
        } catch (IOException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    @PostMapping("/propose")
    @Operation(summary = "Propose an immutable administrative conclusion bound to the original difference")
    public SuccessResponse<ReconciliationReviewEventDTO> propose(@Valid @RequestBody ReconciliationReviewProposeCommand command) {
        return SuccessResponse.of(facade.propose(command));
    }

    @PostMapping("/decide")
    @Operation(summary = "Approve or reject the exact proposal using an independent authorized service")
    public SuccessResponse<ReconciliationReviewEventDTO> decide(@Valid @RequestBody ReconciliationReviewDecideCommand command) {
        return SuccessResponse.of(facade.decide(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read current review and original immutable difference evidence")
    public SuccessResponse<ReconciliationReviewDTO> detail(@Valid @RequestBody ReconciliationReviewQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }

    @PostMapping("/history")
    @Operation(summary = "Read append-only review receipts in version order")
    public PageResponse<ReconciliationReviewEventDTO> history(@Valid @RequestBody ReconciliationReviewHistoryQuery query) {
        return PageResponse.of(facade.history(query));
    }
}
