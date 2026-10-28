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
import io.github.loadup.pay.acquire.app.model.MerchantNotificationRegistration;
import io.github.loadup.pay.acquire.client.dto.RefundNotificationAckDTO;
import io.github.loadup.pay.acquire.domain.model.RefundNotificationReceipt;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundNotificationConverter {
    @Mapping(target = "id", source = "id")
    @Mapping(target = "deliveryId", source = "event.deliveryId")
    @Mapping(target = "businessEventId", source = "event.businessEventId")
    @Mapping(target = "aggregateId", source = "event.aggregateId")
    @Mapping(target = "sourceVersion", source = "event.aggregateVersion")
    @Mapping(target = "orderId", source = "event.data.orderId")
    @Mapping(target = "payeeMerchantId", source = "event.data.payeeMerchantId")
    @Mapping(target = "merchantEventId", source = "registration.merchantEventId")
    @Mapping(target = "disposition", source = "registration.disposition")
    RefundNotificationReceipt toReceipt(String id, EventEnvelope<RefundMerchantEventDTO> event,
                                        MerchantNotificationRegistration registration, String requestDigest, String callerServiceId, LocalDateTime acceptedAt);

    RefundNotificationAckDTO toAck(RefundNotificationReceipt value);
}
