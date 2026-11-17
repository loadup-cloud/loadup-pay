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
package io.github.loadup.admin.config;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.oauth.management.OAuthClientAuthorization;
import io.github.loadup.components.oauth.management.OAuthClientCreateRequest;
import io.github.loadup.components.oauth.management.OAuthClientCredentialResponse;
import io.github.loadup.components.oauth.management.OAuthClientDetails;
import io.github.loadup.components.oauth.management.OAuthClientExpiryRequest;
import io.github.loadup.components.oauth.management.OAuthClientManagementPaths;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(OAuthClientManagementPaths.BASE)
public interface OAuthClientManagementHttpService {
    @PostExchange("/{registrationId}/create")
    SuccessResponse<OAuthClientCredentialResponse> create(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("registrationId") String registrationId,
            @RequestBody OAuthClientCreateRequest request);

    @PostExchange("/{registrationId}/detail")
    SuccessResponse<OAuthClientDetails> detail(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("registrationId") String registrationId);

    @PostExchange("/{registrationId}/authorization")
    SuccessResponse<OAuthClientDetails> authorize(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("registrationId") String registrationId,
            @RequestBody OAuthClientAuthorization request);

    @PostExchange("/{registrationId}/disable")
    SuccessResponse<OAuthClientDetails> disable(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("registrationId") String registrationId);

    @PostExchange("/{registrationId}/expiry")
    SuccessResponse<OAuthClientDetails> expire(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("registrationId") String registrationId,
            @RequestBody OAuthClientExpiryRequest request);

    @PostExchange("/{registrationId}/rotate")
    SuccessResponse<OAuthClientCredentialResponse> rotate(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("registrationId") String registrationId,
            @RequestBody OAuthClientExpiryRequest request);
}
