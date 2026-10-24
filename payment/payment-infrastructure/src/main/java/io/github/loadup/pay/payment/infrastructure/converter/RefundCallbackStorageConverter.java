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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundCallbackStorageConverter {
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "attemptId", source = "originalAttemptId")
    @Mapping(target = "channelRequestNo", source = "originalChannelRequestNo")
    ChannelPaymentIdentity toIdentity(RefundOrderDO row);

    @Mapping(target = "original", source = "original")
    @Mapping(target = "refundId", source = "row.id")
    @Mapping(target = "channelRefundNo", source = "row.channelRefundNo")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "originalAmount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(capacity.getSucceededMinor(), capacity.getCurrency()))")
    @Mapping(target = "originalChannelTransactionNo", source = "row.originalChannelTransactionNo")
    PreparedChannelRefund toPrepared(RefundOrderDO row, PaymentRefundCapacityDO capacity, ChannelPaymentIdentity original);
}
