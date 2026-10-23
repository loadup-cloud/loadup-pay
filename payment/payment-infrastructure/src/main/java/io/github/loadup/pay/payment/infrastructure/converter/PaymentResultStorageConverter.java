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
import io.github.loadup.pay.payment.domain.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.PaymentChannelReceipt;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.PaymentResultAnomaly;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentResultAnomalyDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentResultStorageConverter {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "value.payment.state")
    @Mapping(target = "activeAttemptId", source = "value.payment.activeAttemptId")
    @Mapping(target = "successfulAttemptId", source = "value.payment.successfulAttemptId")
    @Mapping(target = "terminalEvidenceId", source = "value.terminalEvidenceId")
    @Mapping(target = "rowVersion", source = "value.payment.version")
    @Mapping(target = "succeededAt", source = "succeededAt")
    PaymentOrderDO toSuccessPatch(StoredPaymentAcceptance value, java.time.LocalDateTime succeededAt);

    ChannelPaymentIdentity toIdentity(PaymentChannelReceiptDO row);

    ChannelPaymentIdentity toIdentity(ChannelTransactionClaimDO row);

    @Mapping(target = "identity", source = "identity")
    PaymentChannelReceipt toReceipt(PaymentChannelReceiptDO row, ChannelPaymentIdentity identity);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "paymentId", source = "value.identity.paymentId")
    @Mapping(target = "attemptId", source = "value.identity.attemptId")
    @Mapping(target = "channel", source = "value.identity.channel")
    @Mapping(target = "bindingRevisionId", source = "value.identity.bindingRevisionId")
    @Mapping(target = "accountScopeHash", source = "value.identity.accountScopeHash")
    @Mapping(target = "channelRequestNo", source = "value.identity.channelRequestNo")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    PaymentChannelReceiptDO toReceiptDO(String tenant, PaymentChannelReceipt value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    PaymentResultAnomalyDO toAnomalyDO(String tenant, PaymentResultAnomaly value);

    @Mapping(target = "identity", source = "identity")
    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(row.getAmountMinor(), row.getCurrency()))")
    ChannelTransactionClaim toClaim(ChannelTransactionClaimDO row, ChannelPaymentIdentity identity);

    @Mapping(target = "paymentId", source = "identity.paymentId")
    @Mapping(target = "attemptId", source = "identity.attemptId")
    @Mapping(target = "channel", source = "identity.channel")
    @Mapping(target = "bindingRevisionId", source = "identity.bindingRevisionId")
    @Mapping(target = "accountScopeHash", source = "identity.accountScopeHash")
    @Mapping(target = "channelRequestNo", source = "identity.channelRequestNo")
    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    ChannelTransactionClaimDO toClaimDO(ChannelTransactionClaim value);

    @Mapping(target = "succeededAmount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(row.getSucceededMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "reserve", ignore = true)
    PaymentRefundCapacity toCapacity(PaymentRefundCapacityDO row);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "succeededMinor", source = "value.succeededAmount.cent")
    @Mapping(target = "currency", source = "value.succeededAmount.currencyValue")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    PaymentRefundCapacityDO toCapacityDO(String tenant, String id, PaymentRefundCapacity value);
}
