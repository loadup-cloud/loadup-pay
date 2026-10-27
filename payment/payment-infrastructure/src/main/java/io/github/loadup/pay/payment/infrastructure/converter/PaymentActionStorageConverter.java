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
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAction;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentActionDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentActionStorageConverter {
    @Mapping(target = "identity", source = "row")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "executionEpoch", source = "row.executionEpoch")
    @Mapping(target = "action", source = "action")
    @Mapping(target = "source", source = "row.source")
    @Mapping(target = "receiptId", source = "row.receiptId")
    @Mapping(target = "sourceDigest", source = "row.sourceDigest")
    @Mapping(target = "issuedAt", source = "row.issuedAt")
    StoredPaymentAction toDomain(PaymentActionDO row, ChannelPaymentAction action);

    @Mapping(target = "attemptId", source = "id")
    ChannelPaymentIdentity toIdentity(PaymentActionDO row);

    @Mapping(target = "id", source = "value.identity.attemptId")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "paymentId", source = "value.identity.paymentId")
    @Mapping(target = "channel", expression = "java(value.identity().channel().name())")
    @Mapping(target = "bindingRevisionId", source = "value.identity.bindingRevisionId")
    @Mapping(target = "accountScopeHash", source = "value.identity.accountScopeHash")
    @Mapping(target = "channelRequestNo", source = "value.identity.channelRequestNo")
    @Mapping(target = "amountMinor", source = "value.amount.cent")
    @Mapping(target = "currency", source = "value.amount.currencyValue")
    @Mapping(target = "executionEpoch", source = "value.executionEpoch")
    @Mapping(target = "source", source = "value.source")
    @Mapping(target = "receiptId", source = "value.receiptId")
    @Mapping(target = "sourceDigest", source = "value.sourceDigest")
    @Mapping(target = "issuedAt", source = "value.issuedAt")
    @Mapping(target = "expiresAt", source = "expiresAt")
    @Mapping(target = "actionKind", source = "kind")
    @Mapping(target = "actionPayload", source = "payload")
    @Mapping(target = "payloadHash", source = "payloadHash")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    PaymentActionDO toDO(String tenant, StoredPaymentAction value, String kind, String payload,
            String payloadHash, java.time.LocalDateTime expiresAt);
}
