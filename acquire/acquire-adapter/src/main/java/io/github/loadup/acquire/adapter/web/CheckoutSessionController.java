/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
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
 */
package io.github.loadup.acquire.adapter.web;

import io.github.loadup.acquire.client.command.CheckoutSessionCreateCommand;
import io.github.loadup.acquire.client.dto.CheckoutDTO;
import io.github.loadup.acquire.client.facade.CheckoutSessionFacade;
import io.github.loadup.commons.result.SuccessResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/acquire/session")
public class CheckoutSessionController {
    private final CheckoutSessionFacade facade;

    public CheckoutSessionController(CheckoutSessionFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/create")
    public SuccessResponse<CheckoutDTO> create(
            @Valid @RequestBody CheckoutSessionCreateCommand command, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return SuccessResponse.of(facade.create(command));
    }
}
