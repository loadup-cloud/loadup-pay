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
import io.github.loadup.productcenter.client.command.CatalogActionCommand;
import io.github.loadup.productcenter.client.command.CatalogSaveCommand;
import io.github.loadup.productcenter.client.command.ContractStatusCommand;
import io.github.loadup.productcenter.client.command.LimitPolicyPublishCommand;
import io.github.loadup.productcenter.client.command.LimitPolicyReplayCommand;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.dto.LimitPolicyPublicationDTO;
import io.github.loadup.productcenter.client.dto.MerchantContractDTO;
import io.github.loadup.productcenter.client.http.ProductManagementHttpService;
import io.github.loadup.productcenter.client.query.CatalogPageQuery;
import io.github.loadup.productcenter.client.query.MerchantContractPageQuery;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/productmng")
public class ProductCenterManagementController {
    private final ProductManagementHttpService center;
    private final ManagementResponses responses;

    public ProductCenterManagementController(ProductManagementHttpService center, ManagementResponses responses) {
        this.center = center;
        this.responses = responses;
    }

    @PostMapping("/catalog/save")
    @PreAuthorize(
            "hasAuthority('contract:catalog:write') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<CatalogVersionDTO> save(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody CatalogSaveCommand request) {
        return SuccessResponse.of(responses.required(() -> center.save(authorization, request)));
    }

    @PostMapping("/catalog/page")
    @PreAuthorize(
            "hasAuthority('contract:catalog:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public PageResponse<CatalogVersionDTO> catalogPage(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody CatalogPageQuery request) {
        return PageResponse.of(responses.required(
                () -> center.catalogPage(authorization, request).asResponse()));
    }

    @PostMapping("/catalog/detail")
    @PreAuthorize(
            "hasAuthority('contract:catalog:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<CatalogVersionDTO> catalogDetail(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(responses.required(() -> center.catalogDetail(authorization, request)));
    }

    @PostMapping("/catalog/publish")
    @PreAuthorize(
            "hasAuthority('contract:catalog:publish') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<CatalogVersionDTO> publish(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody CatalogActionCommand request) {
        return SuccessResponse.of(responses.required(() -> center.publish(authorization, request)));
    }

    @PostMapping("/catalog/retire")
    @PreAuthorize(
            "hasAuthority('contract:catalog:publish') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<CatalogVersionDTO> retire(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody CatalogActionCommand request) {
        return SuccessResponse.of(responses.required(() -> center.retire(authorization, request)));
    }

    @PostMapping("/merchant-contracts/page")
    @PreAuthorize(
            "hasAuthority('contract:merchant:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public PageResponse<MerchantContractDTO> contractPage(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody MerchantContractPageQuery request) {
        return PageResponse.of(responses.required(
                () -> center.contractPage(authorization, request).asResponse()));
    }

    @PostMapping("/merchant-contracts/detail")
    @PreAuthorize(
            "hasAuthority('contract:merchant:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantContractDTO> contractDetail(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(responses.required(() -> center.contractDetail(authorization, request)));
    }

    @PostMapping("/merchant-contracts/status")
    @PreAuthorize(
            "hasAuthority('contract:merchant:manage') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<MerchantContractDTO> status(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody ContractStatusCommand request) {
        return SuccessResponse.of(responses.required(() -> center.status(authorization, request)));
    }

    @PostMapping("/limit-publications/publish")
    @PreAuthorize(
            "hasAuthority('pay:product:publish') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<LimitPolicyPublicationDTO> publishLimits(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody LimitPolicyPublishCommand request) {
        return SuccessResponse.of(responses.required(() -> center.publishLimits(authorization, request)));
    }

    @PostMapping("/limit-publications/detail")
    @PreAuthorize(
            "hasAuthority('pay:product:publication:read') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<LimitPolicyPublicationDTO> limitPublication(
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody IdQuery request) {
        return SuccessResponse.of(responses.required(() -> center.limitPublication(authorization, request)));
    }

    @PostMapping("/limit-publications/replay")
    @PreAuthorize(
            "hasAuthority('pay:product:publication:replay') and authentication.credentials.getClaimAsString('principal_type') == 'OPERATOR'")
    public SuccessResponse<LimitPolicyPublicationDTO> replayLimits(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody LimitPolicyReplayCommand request) {
        return SuccessResponse.of(responses.required(() -> center.replayLimits(authorization, request)));
    }
}
