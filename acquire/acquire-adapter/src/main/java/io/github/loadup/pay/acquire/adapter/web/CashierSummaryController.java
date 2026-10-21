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
package io.github.loadup.pay.acquire.adapter.web;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.dto.CashierOrderDTO;
import io.github.loadup.pay.acquire.client.facade.CashierSummaryFacade;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashier/order")
public class CashierSummaryController {
    private final CashierSummaryFacade facade;

    public CashierSummaryController(CashierSummaryFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/summary")
    public SuccessResponse<CashierOrderDTO> summary(@Valid @RequestBody IdQuery query, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        return SuccessResponse.of(facade.summary(query));
    }
}
