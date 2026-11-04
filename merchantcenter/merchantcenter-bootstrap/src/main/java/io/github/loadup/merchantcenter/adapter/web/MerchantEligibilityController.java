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

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.merchantcenter.client.command.MerchantActionAuthorizeCommand;
import io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO;
import io.github.loadup.merchantcenter.client.facade.MerchantEligibilityFacade;
import io.github.loadup.merchantcenter.client.http.CustomerHttpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal merchant eligibility")
public class MerchantEligibilityController {
    private final MerchantEligibilityFacade eligibility;

    public MerchantEligibilityController(MerchantEligibilityFacade eligibility) {
        this.eligibility = eligibility;
    }

    @PostMapping(CustomerHttpService.AUTHORIZE_PATH)
    @Operation(summary = "Check merchant eligibility using verified caller identity")
    public SuccessResponse<MerchantEligibilityDTO> authorize(
            @Valid @RequestBody MerchantActionAuthorizeCommand command) {
        return SuccessResponse.of(eligibility.authorize(command));
    }
}
