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
package io.github.loadup.admin.web;

import io.github.loadup.admin.config.OAuthClientManagementHttpService;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.oauth.management.OAuthClientAuthorization;
import io.github.loadup.components.oauth.management.OAuthClientCreateRequest;
import io.github.loadup.components.oauth.management.OAuthClientCredentialResponse;
import io.github.loadup.components.oauth.management.OAuthClientDetails;
import io.github.loadup.components.oauth.management.OAuthClientExpiryRequest;
import io.github.loadup.components.oauth.management.OAuthClientManagement;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth/clients")
public class OAuthClientManagementController {
    private final OAuthClientManagementHttpService clients;
    private final ManagementResponses responses;

    public OAuthClientManagementController(OAuthClientManagementHttpService clients, ManagementResponses responses) {
        this.clients = clients;
        this.responses = responses;
    }

    @PostMapping("/{registrationId}/create")
    @PreAuthorize(
            "hasAuthority('oauth:client:create') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientCredentialResponse>> create(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientCreateRequest request,
            @RequestHeader("Authorization") String authorization) {
        OAuthClientManagement.requireId(registrationId);
        var result = responses.required(() -> clients.create(authorization, registrationId, request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(result));
    }

    @PostMapping("/{registrationId}/detail")
    @PreAuthorize(
            "hasAuthority('oauth:client:read') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> detail(
            @PathVariable("registrationId") String registrationId,
            @RequestHeader("Authorization") String authorization) {
        OAuthClientManagement.requireId(registrationId);
        var result = responses.required(() -> clients.detail(authorization, registrationId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(result));
    }

    @PostMapping("/{registrationId}/authorization")
    @PreAuthorize(
            "hasAuthority('oauth:client:authorize') and authentication.credentials.getClaimAsString('principal_type')"
                    + " == 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> authorize(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientAuthorization request,
            @RequestHeader("Authorization") String authorization) {
        OAuthClientManagement.requireId(registrationId);
        var result = responses.required(() -> clients.authorize(authorization, registrationId, request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(result));
    }

    @PostMapping("/{registrationId}/disable")
    @PreAuthorize(
            "hasAuthority('oauth:client:disable') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> disable(
            @PathVariable("registrationId") String registrationId,
            @RequestHeader("Authorization") String authorization) {
        OAuthClientManagement.requireId(registrationId);
        var result = responses.required(() -> clients.disable(authorization, registrationId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(result));
    }

    @PostMapping("/{registrationId}/expiry")
    @PreAuthorize(
            "hasAuthority('oauth:client:expire') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> expire(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientExpiryRequest request,
            @RequestHeader("Authorization") String authorization) {
        OAuthClientManagement.requireId(registrationId);
        var result = responses.required(() -> clients.expire(authorization, registrationId, request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(result));
    }

    @PostMapping("/{registrationId}/rotate")
    @PreAuthorize(
            "hasAuthority('oauth:client:rotate') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientCredentialResponse>> rotate(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientExpiryRequest request,
            @RequestHeader("Authorization") String authorization) {
        OAuthClientManagement.requireId(registrationId);
        var result = responses.required(() -> clients.rotate(authorization, registrationId, request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.of(result));
    }
}
