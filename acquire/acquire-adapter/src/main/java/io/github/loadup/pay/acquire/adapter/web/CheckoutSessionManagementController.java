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
package io.github.loadup.pay.acquire.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.*;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionRevokeCommand;
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.acquire.client.facade.CheckoutSessionManagementFacade;
import io.github.loadup.pay.acquire.client.query.CheckoutSessionRevocationPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquire/session")
@Tag(name = "Merchant checkout session management")
public class CheckoutSessionManagementController {
    private final CheckoutSessionManagementFacade facade;
    public CheckoutSessionManagementController(CheckoutSessionManagementFacade facade) { this.facade = facade; }
    @PostMapping("/detail")
    @Operation(summary = "Read issuer-owned session metadata without credentials")
    public ResponseEntity<SuccessResponse<CheckoutSessionStatusDTO>> detail(@Valid @RequestBody IdQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.detail(query)));
    }
    @PostMapping("/revoke")
    @Operation(summary = "Permanently revoke one checkout capability; committed payments continue to converge")
    public ResponseEntity<SuccessResponse<CheckoutSessionRevocationDTO>> revoke(@Valid @RequestBody CheckoutSessionRevokeCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.revoke(command)));
    }
    @PostMapping("/revocations")
    @Operation(summary = "Page immutable revocation decisions for an issuer-owned session")
    public ResponseEntity<PageResponse<CheckoutSessionRevocationDTO>> revocations(@Valid @RequestBody CheckoutSessionRevocationPageQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PageResponse.of(facade.revocations(query)));
    }
}
