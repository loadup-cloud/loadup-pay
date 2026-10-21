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
package io.github.loadup.pay.product.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.product.client.command.LimitPolicyPublishCommand;
import io.github.loadup.pay.product.client.command.LimitPolicyReplayCommand;
import io.github.loadup.pay.product.client.dto.LimitPolicyPublicationDTO;
import io.github.loadup.pay.product.client.facade.LimitPolicyPublicationFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product/limit-publications")
@Tag(
        name = "Limit policy publication",
        description = "Privileged service/operator API; HTTP 200 uses SuccessResponse or FailureResponse")
public class LimitPolicyPublicationController {
    private final LimitPolicyPublicationFacade facade;

    public LimitPolicyPublicationController(LimitPolicyPublicationFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/publish")
    @Operation(summary = "Freeze and enqueue the initial signed contract quota policy")
    public SuccessResponse<LimitPolicyPublicationDTO> publish(@Valid @RequestBody LimitPolicyPublishCommand command) {
        return SuccessResponse.of(facade.publish(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Inspect activation state and independent delivery status")
    public SuccessResponse<LimitPolicyPublicationDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }

    @PostMapping("/replay")
    @Operation(summary = "Audit and replay only a failed delivery without changing its intent")
    public SuccessResponse<LimitPolicyPublicationDTO> replay(@Valid @RequestBody LimitPolicyReplayCommand command) {
        return SuccessResponse.of(facade.replay(command));
    }
}
