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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentRealtimeConverter {
    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    @Mapping(target = "state", source = "value.payment.state")
    @Mapping(target = "initiatedAt", source = "value.payment.initiatedAt")
    @Mapping(target = "sourceVersion", source = "value.payment.version")
    PaymentSnapshotEventDTO toSnapshot(StoredPaymentAcceptance value, Channel channel, AccessMode accessMode, LocalDateTime succeededAt);

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
