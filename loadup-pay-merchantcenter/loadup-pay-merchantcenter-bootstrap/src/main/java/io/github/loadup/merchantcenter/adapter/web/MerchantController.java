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
package io.github.loadup.merchantcenter.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantStatusCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.facade.MerchantFacade;
import io.github.loadup.merchantcenter.client.query.MerchantPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/v1/merchantcenter/management/merchants")
@Tag(name = "Merchants", description = "Merchant basic information")
public class MerchantController {
    private final MerchantFacade service;

    public MerchantController(MerchantFacade service) {
        this.service = service;
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('merchant:write') and authentication.credentials.getClaimAsString('principal_type') =="
            + " 'OPERATOR'")
    @Operation(summary = "Create merchant basic information")
    public SuccessResponse<MerchantDTO> create(
            @Valid @RequestBody MerchantCreateCommand command, Authentication actor) {
        return SuccessResponse.of(service.create(command, actor.getName()));
    }

    @PostMapping("/update")
    @PreAuthorize("hasAuthority('merchant:write') and authentication.credentials.getClaimAsString('principal_type') =="
            + " 'OPERATOR'")
    @Operation(summary = "Update merchant with expected version; null private fields preserve data")
    public SuccessResponse<MerchantDTO> update(
            @Valid @RequestBody MerchantUpdateCommand command, Authentication actor) {
        return SuccessResponse.of(service.update(command, actor.getName()));
    }

    @PostMapping("/page")
    @PreAuthorize("hasAuthority('merchant:read') and authentication.credentials.getClaimAsString('principal_type') =="
            + " 'OPERATOR'")
    @Operation(summary = "Page merchants")
    public PageResponse<MerchantDTO> page(@Valid @RequestBody MerchantPageQuery query) {
        return PageResponse.of(service.page(query));
    }

    @PostMapping("/detail")
    @PreAuthorize("hasAuthority('merchant:read') and authentication.credentials.getClaimAsString('principal_type') =="
            + " 'OPERATOR'")
    @Operation(summary = "Read merchant with masked private data")
    public SuccessResponse<MerchantDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(service.detail(query.id()));
    }

    @PostMapping("/status")
    @PreAuthorize("hasAuthority('merchant:manage') and authentication.credentials.getClaimAsString('principal_type') =="
            + " 'OPERATOR'")
    @Operation(summary = "Enable or disable merchant with expected version")
    public SuccessResponse<MerchantDTO> status(
            @Valid @RequestBody MerchantStatusCommand command, Authentication actor) {
        return SuccessResponse.of(service.changeStatus(command, actor.getName()));
    }
}
