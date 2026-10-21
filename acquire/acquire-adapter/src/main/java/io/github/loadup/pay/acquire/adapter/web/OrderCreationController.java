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
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderCreationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/acquire/orders")
@Tag(name = "Acquiring orders")
public class OrderCreationController {
    private final AcquireOrderCreationFacade facade;

    public OrderCreationController(AcquireOrderCreationFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/create")
    @Operation(summary = "Create or replay one fixed merchant order intent")
    public SuccessResponse<AcquireOrderDTO> create(@Valid @RequestBody AcquireOrderCreateCommand command) {
        return SuccessResponse.of(facade.create(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read an order within its original caller scope")
    public SuccessResponse<AcquireOrderDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }
}
