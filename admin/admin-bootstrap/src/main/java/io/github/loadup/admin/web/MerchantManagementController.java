/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.admin.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.command.MerchantActionGrantCommand;
import io.github.loadup.merchantcenter.client.command.MerchantActionGrantRevokeCommand;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantRelationshipCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantRelationshipRevokeCommand;
import io.github.loadup.merchantcenter.client.command.MerchantStatusCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantActionGrantDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.dto.MerchantRelationshipDTO;
import io.github.loadup.merchantcenter.client.http.MerchantManagementHttpService;
import io.github.loadup.merchantcenter.client.query.MerchantPageQuery;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchantmng")
public class MerchantManagementController {
    private final MerchantManagementHttpService center;
    private final ManagementResponses responses;

    public MerchantManagementController(MerchantManagementHttpService center, ManagementResponses responses) {
        this.center = center;
        this.responses = responses;
    }

    @PostMapping("/merchants/create")
    @PreAuthorize(
            "hasAuthority('merchant:write') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantDTO> create(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody MerchantCreateCommand request) {
        return SuccessResponse.of(responses.required(() -> center.create(authorization, request)));
    }

    @PostMapping("/merchants/update")
    @PreAuthorize(
            "hasAuthority('merchant:write') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantDTO> update(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody MerchantUpdateCommand request) {
        return SuccessResponse.of(responses.required(() -> center.update(authorization, request)));
    }

    @PostMapping("/merchants/page")
    @PreAuthorize(
            "hasAuthority('merchant:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public PageResponse<MerchantDTO> page(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody MerchantPageQuery request) {
        return PageResponse.of(
                responses.required(() -> center.page(authorization, request).asResponse()));
    }

    @PostMapping("/merchants/detail")
    @PreAuthorize(
            "hasAuthority('merchant:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantDTO> detail(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(responses.required(() -> center.detail(authorization, request)));
    }

    @PostMapping("/merchants/status")
    @PreAuthorize(
            "hasAuthority('merchant:manage') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantDTO> status(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody MerchantStatusCommand request) {
        return SuccessResponse.of(responses.required(() -> center.status(authorization, request)));
    }

    @PostMapping("/relationships/create")
    @PreAuthorize(
            "hasAuthority('pay:customer:manage') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantRelationshipDTO> createRelationship(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MerchantRelationshipCreateCommand request) {
        return SuccessResponse.of(responses.required(() -> center.createRelationship(authorization, request)));
    }

    @PostMapping("/relationships/revoke")
    @PreAuthorize(
            "hasAuthority('pay:customer:manage') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantRelationshipDTO> revokeRelationship(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MerchantRelationshipRevokeCommand request) {
        return SuccessResponse.of(responses.required(() -> center.revokeRelationship(authorization, request)));
    }

    @PostMapping("/grants/create")
    @PreAuthorize(
            "hasAuthority('pay:customer:manage') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantActionGrantDTO> grant(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MerchantActionGrantCommand request) {
        return SuccessResponse.of(responses.required(() -> center.grant(authorization, request)));
    }

    @PostMapping("/grants/revoke")
    @PreAuthorize(
            "hasAuthority('pay:customer:manage') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantActionGrantDTO> revokeGrant(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MerchantActionGrantRevokeCommand request) {
        return SuccessResponse.of(responses.required(() -> center.revokeGrant(authorization, request)));
    }
}
