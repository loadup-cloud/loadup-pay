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
package io.github.loadup.oauth2server;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.oauth.management.OAuthClientAuthorization;
import io.github.loadup.components.oauth.management.OAuthClientCreateRequest;
import io.github.loadup.components.oauth.management.OAuthClientCredentialResponse;
import io.github.loadup.components.oauth.management.OAuthClientDetails;
import io.github.loadup.components.oauth.management.OAuthClientExpiryRequest;
import io.github.loadup.components.oauth.management.OAuthClientManagementPaths;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(OAuthClientManagementPaths.BASE)
public class OAuthClientManagementController {
    private final OAuthClientManagementService service;

    public OAuthClientManagementController(OAuthClientManagementService service) {
        this.service = service;
    }

    @PostMapping("/{registrationId}/create")
    @PreAuthorize(
            "hasAuthority('oauth:client:create') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientCredentialResponse>> create(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientCreateRequest request,
            Authentication actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.of(service.create(registrationId, request, actor)));
    }

    @PostMapping("/{registrationId}/detail")
    @PreAuthorize(
            "hasAuthority('oauth:client:read') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> detail(
            @PathVariable("registrationId") String registrationId, Authentication actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.of(service.detail(registrationId, actor)));
    }

    @PostMapping("/{registrationId}/authorization")
    @PreAuthorize(
            "hasAuthority('oauth:client:authorize') and authentication.credentials.getClaimAsString('principal_type')"
                    + " == 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> authorize(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientAuthorization request,
            Authentication actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.of(service.authorize(registrationId, request, actor)));
    }

    @PostMapping("/{registrationId}/disable")
    @PreAuthorize(
            "hasAuthority('oauth:client:disable') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> disable(
            @PathVariable("registrationId") String registrationId, Authentication actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.of(service.disable(registrationId, actor)));
    }

    @PostMapping("/{registrationId}/expiry")
    @PreAuthorize(
            "hasAuthority('oauth:client:expire') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientDetails>> expire(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientExpiryRequest request,
            Authentication actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.of(service.expire(registrationId, request, actor)));
    }

    @PostMapping("/{registrationId}/rotate")
    @PreAuthorize(
            "hasAuthority('oauth:client:rotate') and authentication.credentials.getClaimAsString('principal_type') =="
                    + " 'OPERATOR'")
    public ResponseEntity<SuccessResponse<OAuthClientCredentialResponse>> rotate(
            @PathVariable("registrationId") String registrationId,
            @Valid @RequestBody OAuthClientExpiryRequest request,
            Authentication actor) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SuccessResponse.of(service.rotate(registrationId, request, actor)));
    }
}
