/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
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
 */
package io.github.loadup.realtime.adapter.web;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.contracts.event.OrderRefundSummaryEventDTO;
import io.github.loadup.contracts.event.OrderSnapshotEventDTO;
import io.github.loadup.contracts.event.PaymentSnapshotEventDTO;
import io.github.loadup.contracts.event.RefundSnapshotEventDTO;
import io.github.loadup.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.realtime.client.facade.TradeProjectionFacade;
import io.github.loadup.realtime.client.http.RealtimeHttpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal projections")
public class TradeProjectionController {
    private final TradeProjectionFacade projection;

    public TradeProjectionController(TradeProjectionFacade projection) {
        this.projection = projection;
    }

    @PostMapping(RealtimeHttpService.ORDER_PATH)
    @Operation(summary = "Order using verified caller identity")
    public SuccessResponse<ProjectionAckDTO> order(@Valid @RequestBody EventEnvelope<OrderSnapshotEventDTO> request) {
        return SuccessResponse.of(projection.applyOrder(request));
    }

    @PostMapping(RealtimeHttpService.PAYMENT_PATH)
    @Operation(summary = "Payment using verified caller identity")
    public SuccessResponse<ProjectionAckDTO> payment(
            @Valid @RequestBody EventEnvelope<PaymentSnapshotEventDTO> request) {
        return SuccessResponse.of(projection.applyPayment(request));
    }

    @PostMapping(RealtimeHttpService.REFUND_PATH)
    @Operation(summary = "Refund using verified caller identity")
    public SuccessResponse<ProjectionAckDTO> refund(@Valid @RequestBody EventEnvelope<RefundSnapshotEventDTO> request) {
        return SuccessResponse.of(projection.applyRefund(request));
    }

    @PostMapping(RealtimeHttpService.REFUND_SUMMARY_PATH)
    @Operation(summary = "RefundSummary using verified caller identity")
    public SuccessResponse<ProjectionAckDTO> refundSummary(
            @Valid @RequestBody EventEnvelope<OrderRefundSummaryEventDTO> request) {
        return SuccessResponse.of(projection.applyRefundSummary(request));
    }
}
