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
package io.github.loadup.pay.limitcenter.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.limitcenter.client.command.*;
import io.github.loadup.pay.limitcenter.client.dto.*;
import io.github.loadup.pay.limitcenter.client.facade.LimitSettlementFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/limit/settlement")
@Tag(name = "Trusted merchant limit settlement")
public class LimitSettlementController {
    private final LimitSettlementFacade facade;
    public LimitSettlementController(LimitSettlementFacade facade) { this.facade = facade; }
    @PostMapping("/confirm") @Operation(summary = "Consume held quota from authoritative successful payment facts")
    public SuccessResponse<LimitReservationDTO> confirm(@Valid @RequestBody LimitConfirmCommand command) {
        return SuccessResponse.of(facade.confirm(command));
    }
    @PostMapping("/release") @Operation(summary = "Fence an operation and release quota only with trusted no-collection facts")
    public SuccessResponse<LimitReleaseDTO> release(@Valid @RequestBody LimitReleaseCommand command) {
        return SuccessResponse.of(facade.release(command));
    }
}
