/*
 * #%L
 * LoadUp Pay Product Adapter
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.product.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.product.client.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import io.github.loadup.pay.product.client.facade.OrderAdmissionFacade;
import io.github.loadup.pay.product.client.http.ProductHttpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal order admissions")
public class OrderAdmissionController {
    private final OrderAdmissionFacade admissions;

    public OrderAdmissionController(OrderAdmissionFacade admissions) {
        this.admissions = admissions;
    }

    @PostMapping(ProductHttpService.AUTHORIZE_PATH)
    @Operation(summary = "Authorize an order using the original creation intent")
    public SuccessResponse<OrderAdmissionDTO> authorize(@Valid @RequestBody OrderAdmissionAuthorizeCommand command) {
        return SuccessResponse.of(admissions.authorize(command));
    }

    @PostMapping(ProductHttpService.DETAIL_PATH)
    @Operation(summary = "Read an existing admission for its verified owner")
    public SuccessResponse<OrderAdmissionDTO> detail(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(admissions.detail(query));
    }
}
