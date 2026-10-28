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
package io.github.loadup.pay.acquire.app.converter;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.domain.model.FeeRefundReceipt;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface FeeRefundConverter {
    @Mapping(target = "orderId", source = "event.data.orderId")
    @Mapping(target = "sourceVersion", source = "event.aggregateVersion")
    FeeRefundReceipt toReceipt(String id, EventEnvelope<OrderRefundSummaryEventDTO> event, long appliedVersion,
                               String requestDigest, String callerServiceId, LocalDateTime acceptedAt);

    @Mapping(target = "accepted", constant = "true")
    FeeRefundAckDTO toAck(FeeRefundReceipt value);
}
