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
import io.github.loadup.pay.payment.domain.model.RefundAcceptanceEvidence;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundAcceptanceStorageConverter {
    ChannelPaymentIdentity toIdentity(RefundAcceptanceDO row);

    @Mapping(target = "refundId", source = "row.id")
    @Mapping(target = "original", source = "row")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    RefundAcceptanceEvidence toModel(RefundAcceptanceDO row);

    @Mapping(target = "id", source = "value.refundId")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "paymentId", source = "value.original.paymentId")
    @Mapping(target = "attemptId", source = "value.original.attemptId")
    @Mapping(target = "channel", source = "value.original.channel")
    @Mapping(target = "bindingRevisionId", source = "value.original.bindingRevisionId")
    @Mapping(target = "accountScopeHash", source = "value.original.accountScopeHash")
    @Mapping(target = "channelRequestNo", source = "value.original.channelRequestNo")
    @Mapping(target = "originalChannelTransactionNo", source = "value.originalChannelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.channelRefundNo")
    @Mapping(target = "amountMinor", source = "value.amount.cent")
    @Mapping(target = "currency", source = "value.amount.currencyValue")
    @Mapping(target = "acceptedAt", source = "value.acceptedAt")
    @Mapping(target = "receiptId", source = "value.receiptId")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    RefundAcceptanceDO toDO(String tenant, RefundAcceptanceEvidence value);
}
