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
package io.github.loadup.pay.customer.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.customer.client.command.MerchantActionGrantCommand;
import io.github.loadup.pay.customer.client.command.MerchantActionGrantRevokeCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipCreateCommand;
import io.github.loadup.pay.customer.client.command.MerchantRelationshipRevokeCommand;
import io.github.loadup.pay.customer.client.dto.MerchantActionGrantDTO;
import io.github.loadup.pay.customer.client.dto.MerchantRelationshipDTO;
import io.github.loadup.pay.customer.client.facade.MerchantAuthorizationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer")
@Tag(
        name = "Merchant authorization",
        description = "HTTP 200 responses use SuccessResponse or FailureResponse envelopes")
public class MerchantAuthorizationController {
    private final MerchantAuthorizationFacade authorization;

    public MerchantAuthorizationController(
            MerchantAuthorizationFacade authorization) {
        this.authorization = authorization;
    }

    @PostMapping("/relationships/create")
    @Operation(summary = "Create an explicit merchant management relationship")
    public SuccessResponse<MerchantRelationshipDTO> create(
            @Valid @RequestBody MerchantRelationshipCreateCommand command) {
        return SuccessResponse.of(authorization.createRelationship(command));
    }

    @PostMapping("/relationships/revoke")
    @Operation(summary = "Revoke a merchant management relationship")
    public SuccessResponse<MerchantRelationshipDTO> revoke(
            @Valid @RequestBody MerchantRelationshipRevokeCommand command) {
        return SuccessResponse.of(authorization.revokeRelationship(command));
    }

    @PostMapping("/grants/create")
    @Operation(summary = "Grant one explicit action to a principal")
    public SuccessResponse<MerchantActionGrantDTO> grant(@Valid @RequestBody MerchantActionGrantCommand command) {
        return SuccessResponse.of(authorization.grant(command));
    }

    @PostMapping("/grants/revoke")
    @Operation(summary = "Revoke one action grant")
    public SuccessResponse<MerchantActionGrantDTO> revokeGrant(
            @Valid @RequestBody MerchantActionGrantRevokeCommand command) {
        return SuccessResponse.of(authorization.revokeGrant(command));
    }

}
