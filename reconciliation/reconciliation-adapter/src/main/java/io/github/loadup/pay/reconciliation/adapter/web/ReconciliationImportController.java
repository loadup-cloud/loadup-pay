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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.reconciliation.client.command.*;
import io.github.loadup.pay.reconciliation.client.dto.*;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationImportFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;

@RestController
@RequestMapping("/api/reconciliation/imports")
@Tag(name = "Manual channel statement import operations")
public class ReconciliationImportController {
    private final ReconciliationImportFacade facade;
    public ReconciliationImportController(ReconciliationImportFacade facade) { this.facade = facade; }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an owned bounded source; content hash does not prove channel provenance")
    public SuccessResponse<ReconciliationSourceFileDTO> upload(@RequestParam("file") MultipartFile file) {
        try (var source = file.getInputStream()) {
            return SuccessResponse.of(facade.upload(file.getOriginalFilename(), file.getContentType(), file.getSize(), source));
        } catch (IOException failure) { throw new CommonException(DEPENDENCY_UNAVAILABLE); }
    }
    @PostMapping("/submit") @Operation(summary = "Atomically pin the original file and register one manual CHANNEL import")
    public SuccessResponse<ReconciliationImportDTO> submit(@Valid @RequestBody ReconciliationChannelImportCommand command) {
        return SuccessResponse.of(facade.submit(command));
    }
    @PostMapping("/detail") @Operation(summary = "Read logical sealing and independent native task status")
    public SuccessResponse<ReconciliationImportDTO> detail(@Valid @RequestBody IdQuery query) { return SuccessResponse.of(facade.detail(query)); }
    @PostMapping("/retry") @Operation(summary = "Explicitly retry the original failed task without replacing source or reclaiming a running task")
    public SuccessResponse<ReconciliationImportDTO> retry(@Valid @RequestBody ReconciliationImportRetryCommand command) {
        return SuccessResponse.of(facade.retry(command));
    }
}
