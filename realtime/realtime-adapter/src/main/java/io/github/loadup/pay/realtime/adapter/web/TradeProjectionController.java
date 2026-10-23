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

import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import io.github.loadup.pay.realtime.client.facade.TradeProjectionFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime/projections")
@Tag(name = "Trusted independent trade projections")
public class TradeProjectionController {
    private final TradeProjectionFacade facade;

    public TradeProjectionController(TradeProjectionFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/order")
    @Operation(summary = "Atomically apply a complete acquire order snapshot or acknowledge a stale version")
    public SuccessResponse<ProjectionAckDTO> order(@RequestBody PayEventEnvelope<OrderSnapshotEventDTO> event) {
        return SuccessResponse.of(facade.applyOrder(event));
    }

    @PostMapping("/payment")
    @Operation(summary = "Apply a complete payment snapshot without querying its source service")
    public SuccessResponse<ProjectionAckDTO> payment(@RequestBody PayEventEnvelope<PaymentSnapshotEventDTO> event) {
        return SuccessResponse.of(facade.applyPayment(event));
    }

    @PostMapping("/refund")
    @Operation(summary = "Apply an independent child refund snapshot")
    public SuccessResponse<ProjectionAckDTO> refund(@RequestBody PayEventEnvelope<RefundSnapshotEventDTO> event) {
        return SuccessResponse.of(facade.applyRefund(event));
    }

    @PostMapping("/refund-summary")
    @Operation(summary = "Apply the independently versioned complete refund summary")
    public SuccessResponse<ProjectionAckDTO> summary(@RequestBody PayEventEnvelope<OrderRefundSummaryEventDTO> event) {
        return SuccessResponse.of(facade.applyRefundSummary(event));
    }
}
