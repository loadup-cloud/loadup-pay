/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.*;
import io.github.loadup.pay.acquire.client.command.MerchantNotificationWakeCommand;
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.acquire.client.facade.*;
import io.github.loadup.pay.acquire.client.query.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquire/notifications")
@Tag(name = "Merchant business notifications")
public class MerchantNotificationController {
    private final MerchantNotificationQueryFacade queries;
    private final MerchantNotificationOperationsFacade operations;
    public MerchantNotificationController(MerchantNotificationQueryFacade queries, MerchantNotificationOperationsFacade operations) {
        this.queries = queries; this.operations = operations;
    }
    @PostMapping("/detail")
    @Operation(summary = "Read notification delivery metadata within the authenticated merchant scope")
    public ResponseEntity<SuccessResponse<MerchantNotificationDTO>> detail(@Valid @RequestBody IdQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(queries.detail(query)));
    }
    @PostMapping("/page")
    @Operation(summary = "Page notifications for one collecting merchant")
    public ResponseEntity<PageResponse<MerchantNotificationDTO>> page(@Valid @RequestBody MerchantNotificationPageQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.of(queries.page(query)));
    }
    @PostMapping("/wake")
    @Operation(summary = "Audit and resume the current native task within the frozen business budget; operators only")
    public ResponseEntity<SuccessResponse<MerchantNotificationWakeDTO>> wake(@Valid @RequestBody MerchantNotificationWakeCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(operations.wake(command)));
    }
    @PostMapping("/wakes")
    @Operation(summary = "Page immutable wake decisions for an original notification; operators only")
    public ResponseEntity<PageResponse<MerchantNotificationWakeDTO>> wakes(@Valid @RequestBody MerchantNotificationWakePageQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.of(operations.wakes(query)));
    }
}
