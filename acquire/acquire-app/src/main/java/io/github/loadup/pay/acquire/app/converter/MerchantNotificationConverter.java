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

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.app.model.MerchantNotificationData;
import io.github.loadup.pay.acquire.app.model.MerchantRefundNotificationData;
import io.github.loadup.pay.acquire.app.model.OrderSnapshotFacts;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface MerchantNotificationConverter {
    MerchantNotificationPolicy toPolicy(NotificationTermsDTO value);

    @Mapping(target = "orderId", source = "facts.orderId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "payeeMerchantId", source = "facts.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "amountMinor")
    @Mapping(target = "currency", expression = "java(facts.amount().getCurrencyValue())")
    @Mapping(target = "orderPaidAmountMinor", source = "facts.paidMinor")
    @Mapping(target = "orderAmountMinor", expression = "java(facts.amount().getCent())")
    @Mapping(target = "orderState", source = "facts.state")
    MerchantNotificationData toData(OrderSnapshotFacts facts, String paymentId, long amountMinor);

    MerchantRefundNotificationData toRefundData(io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO value);
}
