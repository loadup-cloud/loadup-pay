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
package io.github.loadup.pay.payment.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.command.RoutingProfilePublishCommand;
import io.github.loadup.pay.payment.client.dto.RoutingProfileDTO;
import io.github.loadup.pay.payment.client.facade.RoutingProfileFacade;
import io.github.loadup.pay.payment.client.query.RoutingProfileDetailQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/routing-profiles")
@Tag(name = "Trusted immutable routing profiles")
public class RoutingProfileController {
    private final RoutingProfileFacade facade;

    public RoutingProfileController(RoutingProfileFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish an immutable ordered routing version for a collecting merchant")
    public SuccessResponse<RoutingProfileDTO> publish(@Valid @RequestBody RoutingProfilePublishCommand command) {
        return SuccessResponse.of(facade.publish(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read an immutable routing version within the declared merchant scope")
    public SuccessResponse<RoutingProfileDTO> detail(@Valid @RequestBody RoutingProfileDetailQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }
}
