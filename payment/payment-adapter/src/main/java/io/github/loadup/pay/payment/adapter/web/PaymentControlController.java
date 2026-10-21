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
import io.github.loadup.pay.payment.client.command.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.facade.PaymentControlFacade;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/executions")
@Tag(name = "Payment execution control")
public class PaymentControlController {
    private final PaymentControlFacade facade;

    public PaymentControlController(PaymentControlFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/close")
    @Operation(summary = "Fence one payment and close locally only when no collection is proven")
    public SuccessResponse<PaymentCloseDTO> close(@Valid @RequestBody PaymentCloseCommand command) {
        return SuccessResponse.of(facade.close(command));
    }

    @PostMapping("/fence-parent")
    @Operation(summary = "Persist a monotonic parent fence and its request receipt")
    public SuccessResponse<ParentFenceDTO> fenceParent(@Valid @RequestBody ParentPaymentFenceCommand command) {
        return SuccessResponse.of(facade.fenceParent(command));
    }

    @PostMapping("/parent-fence-status")
    @Operation(summary = "Observe child convergence while holding the parent gate")
    public SuccessResponse<ParentFenceDTO> parentFenceStatus(@Valid @RequestBody ParentFenceStatusQuery query) {
        return SuccessResponse.of(facade.parentFenceStatus(query));
    }
}
