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

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.payment.client.command.RefundCreateCommand;
import io.github.loadup.pay.payment.client.dto.RefundRequestDTO;
import io.github.loadup.pay.payment.client.facade.RefundFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment/refunds")
@Tag(name = "Merchant refunds")
public class RefundController {
    private final RefundFacade refunds;

    public RefundController(RefundFacade refunds) {
        this.refunds = refunds;
    }

    @PostMapping("/create")
    @Operation(summary = "Reserve a fixed original-route refund plan or replay the merchant refund number")
    public SuccessResponse<RefundRequestDTO> create(@Valid @RequestBody RefundCreateCommand command) {
        return SuccessResponse.of(refunds.create(command));
    }

    @PostMapping("/detail")
    @Operation(summary = "Read the authenticated merchant's original refund request and fixed allocations")
    public SuccessResponse<RefundRequestDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(refunds.detail(query));
    }
}
