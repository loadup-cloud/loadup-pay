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
package io.github.loadup.pay.realtime.adapter.web;

import io.github.loadup.commons.result.PageResponse;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.realtime.client.dto.TradeDetailDTO;
import io.github.loadup.pay.realtime.client.dto.TradeOrderDTO;
import io.github.loadup.pay.realtime.client.dto.TradePaymentDTO;
import io.github.loadup.pay.realtime.client.dto.TradeRefundDTO;
import io.github.loadup.pay.realtime.client.facade.MerchantTradeQueryFacade;
import io.github.loadup.pay.realtime.client.query.TradeDetailQuery;
import io.github.loadup.pay.realtime.client.query.TradeOrderPageQuery;
import io.github.loadup.pay.realtime.client.query.TradePaymentPageQuery;
import io.github.loadup.pay.realtime.client.query.TradeRefundPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime/trades")
@Tag(name = "Merchant scoped eventual trade queries")
public class MerchantTradeQueryController {
    private final MerchantTradeQueryFacade facade;

    public MerchantTradeQueryController(MerchantTradeQueryFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/detail")
    @Operation(summary = "Read the projected order header and independent refund summary")
    public SuccessResponse<TradeDetailDTO> detail(@Valid @RequestBody TradeDetailQuery query) {
        return SuccessResponse.of(facade.detail(query));
    }

    @PostMapping("/orders")
    @Operation(summary = "Page projected orders within the verified merchant scope")
    public PageResponse<TradeOrderDTO> orders(@Valid @RequestBody TradeOrderPageQuery query) {
        return PageResponse.of(facade.page(query));
    }

    @PostMapping("/payments")
    @Operation(summary = "Page individual payments, including payments whose order snapshot has not arrived")
    public PageResponse<TradePaymentDTO> payments(@Valid @RequestBody TradePaymentPageQuery query) {
        return PageResponse.of(facade.payments(query));
    }

    @PostMapping("/refunds")
    @Operation(summary = "Page independent child refunds")
    public PageResponse<TradeRefundDTO> refunds(@Valid @RequestBody TradeRefundPageQuery query) {
        return PageResponse.of(facade.refunds(query));
    }
}
