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
package io.github.loadup.merchantcenter.adapter.web;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.adapter.security.AuthenticatedTenant;
import io.github.loadup.merchantcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.merchantcenter.client.error.MerchantResultCodeEnum;
import io.github.loadup.merchantcenter.client.facade.MerchantQueryFacade;
import io.github.loadup.merchantcenter.client.http.MerchantProfileHttpService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MerchantProfileController {
    private final MerchantQueryFacade merchants;

    public MerchantProfileController(MerchantQueryFacade merchants) {
        this.merchants = merchants;
    }

    @PostMapping(MerchantProfileHttpService.PROFILE_PATH)
    @PreAuthorize("hasAuthority('pay:merchant:profile:read')")
    public SuccessResponse<MerchantProfileDTO> profile(@Valid @RequestBody IdQuery query, Authentication actor) {
        return AuthenticatedTenant.call(
                actor,
                () -> SuccessResponse.of(merchants
                        .find(query.id())
                        .orElseThrow(() -> new CommonException(MerchantResultCodeEnum.NOT_FOUND))));
    }
}
