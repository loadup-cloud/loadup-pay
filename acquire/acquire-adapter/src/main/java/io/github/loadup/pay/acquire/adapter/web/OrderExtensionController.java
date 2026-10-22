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

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.command.AcquireOrderExtendCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderExtensionFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquire/orders")
@Tag(name = "Merchant order deadline extension")
public class OrderExtensionController {
    private final AcquireOrderExtensionFacade facade;
    public OrderExtensionController(AcquireOrderExtensionFacade facade) { this.facade = facade; }

    @PostMapping("/extend")
    @Operation(summary = "Extend an original order deadline within the frozen cumulative allowance")
    public ResponseEntity<SuccessResponse<AcquireOrderDTO>> extend(@Valid @RequestBody AcquireOrderExtendCommand command) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(facade.extend(command)));
    }
}
