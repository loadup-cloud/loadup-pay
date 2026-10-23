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
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundChannelReceipt;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.RefundResultAnomaly;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRequestDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundResultAnomalyDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundTransactionClaimDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundResultStorageConverter {
    RefundChannelReceipt toReceipt(RefundChannelReceiptDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    RefundChannelReceiptDO toReceiptDO(String tenant, RefundChannelReceipt value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    RefundResultAnomalyDO toAnomalyDO(String tenant, RefundResultAnomaly value);

    ChannelPaymentIdentity toIdentity(RefundTransactionClaimDO row);

    @Mapping(target = "original", source = "original")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    RefundTransactionClaim toClaim(RefundTransactionClaimDO row, ChannelPaymentIdentity original);

    @Mapping(target = "paymentId", source = "original.paymentId")
    @Mapping(target = "attemptId", source = "original.attemptId")
    @Mapping(target = "channel", source = "original.channel")
    @Mapping(target = "bindingRevisionId", source = "original.bindingRevisionId")
    @Mapping(target = "accountScopeHash", source = "original.accountScopeHash")
    @Mapping(target = "channelRequestNo", source = "original.channelRequestNo")
    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    RefundTransactionClaimDO toClaimDO(RefundTransactionClaim value);

    @Mapping(target = "child", source = "child")
    RefundExecutionFacts toFacts(RefundOrderDO row, StoredRefundChild child);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "refund.state")
    @Mapping(target = "terminalEvidenceId", source = "refund.terminalEvidenceId")
    @Mapping(target = "rowVersion", source = "refund.version")
    @Mapping(target = "submissionStartedAt", source = "submissionStartedAt")
    @Mapping(target = "channelRefundTransactionNo", source = "channelRefundTransactionNo")
    @Mapping(target = "succeededAt", source = "succeededAt")
    RefundOrderDO toChildPatch(RefundProgressChange value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "state")
    @Mapping(target = "rowVersion", source = "version")
    @Mapping(target = "succeededMinor", expression = "java(value.succeededMinor())")
    @Mapping(target = "failedMinor", expression = "java(value.failedMinor())")
    @Mapping(target = "unknownCount", expression = "java(Math.toIntExact(value.unknownCount()))")
    RefundRequestDO toRequestPatch(RefundRequest value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "reservedMinor", source = "reservedMinor")
    @Mapping(target = "refundedMinor", source = "refundedMinor")
    @Mapping(target = "rowVersion", source = "version")
    PaymentRefundCapacityDO toCapacityPatch(PaymentRefundCapacity value);
}
