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
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.facade.PaymentSettlementFactsFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/facts")
@Tag(name = "Internal payment settlement evidence")
public class PaymentSettlementFactsController {
    private final PaymentSettlementFactsFacade facade;
    public PaymentSettlementFactsController(PaymentSettlementFactsFacade facade) { this.facade = facade; }
    @PostMapping("/payment")
    @Operation(summary = "Read authoritative payment facts for the trusted limit service")
    public SuccessResponse<PaymentSettlementFactsDTO> payment(@Valid @RequestBody IdQuery query) {
        return SuccessResponse.of(facade.paymentFacts(query));
    }
}
