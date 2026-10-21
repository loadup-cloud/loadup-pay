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
package io.github.loadup.pay.limitcenter.adapter.web;

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.limitcenter.client.command.LimitBeginExecutionCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExecutionVerifyCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitExpireCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.command.LimitReserveCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.facade.LimitExecutionFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitPolicyFacade;
import io.github.loadup.pay.limitcenter.client.facade.LimitReservationFacade;
import io.github.loadup.pay.limitcenter.client.query.LimitOperationQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/limitcenter")
@Tag(
        name = "Merchant limits",
        description = "Service credentials required; HTTP 200 uses SuccessResponse or FailureResponse envelopes")
public class LimitCenterController {
    private final LimitPolicyFacade policies;
    private final LimitReservationFacade reservations;
    private final LimitExecutionFacade executions;

    public LimitCenterController(
            LimitPolicyFacade policies, LimitReservationFacade reservations, LimitExecutionFacade executions) {
        this.policies = policies;
        this.reservations = reservations;
        this.executions = executions;
    }

    @PostMapping("/policies/activate")
    @Operation(summary = "Activate or replay a complete immutable policy revision")
    public SuccessResponse<LimitPolicyActivationDTO> activate(@Valid @RequestBody LimitPolicyActivateCommand command) {
        return SuccessResponse.of(policies.activate(command));
    }

    @PostMapping("/reservations/reserve")
    @Operation(summary = "Atomically reserve all amount and payment count windows")
    public SuccessResponse<LimitReservationDTO> reserve(@Valid @RequestBody LimitReserveCommand command) {
        return SuccessResponse.of(reservations.reserve(command));
    }

    @PostMapping("/reservations/detail")
    @Operation(summary = "Read a reservation owned by the calling service")
    public SuccessResponse<LimitReservationDTO> detail(@Valid @RequestBody LimitOperationQuery query) {
        return SuccessResponse.of(reservations.detail(query));
    }

    @PostMapping("/executions/begin")
    @Operation(summary = "Activate one channel attempt before reservation expiry")
    public SuccessResponse<LimitExecutionPermitDTO> begin(@Valid @RequestBody LimitBeginExecutionCommand command) {
        return SuccessResponse.of(executions.beginExecution(command));
    }

    @PostMapping("/executions/verify")
    @Operation(summary = "Observe the current attempt, epoch and opaque permit binding")
    public SuccessResponse<LimitExecutionVerificationDTO> verify(
            @Valid @RequestBody LimitExecutionVerifyCommand command) {
        return SuccessResponse.of(executions.verifyExecution(command));
    }

    @PostMapping("/reservations/expire")
    @Operation(summary = "Release only an expired reservation that never began execution")
    public SuccessResponse<LimitReservationDTO> expire(@Valid @RequestBody LimitExpireCommand command) {
        return SuccessResponse.of(executions.expire(command));
    }
}
