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
package io.github.loadup.pay.realtime.client.facade;

import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;

/**
 * Business use cases; identity and authorization come from a trusted invocation context.
 */
public interface TradeProjectionFacade {
    ProjectionAckDTO applyOrder(EventEnvelope<OrderSnapshotEventDTO> event);

    ProjectionAckDTO applyPayment(EventEnvelope<PaymentSnapshotEventDTO> event);

    ProjectionAckDTO applyRefund(EventEnvelope<RefundSnapshotEventDTO> event);

    ProjectionAckDTO applyRefundSummary(EventEnvelope<OrderRefundSummaryEventDTO> event);
}
