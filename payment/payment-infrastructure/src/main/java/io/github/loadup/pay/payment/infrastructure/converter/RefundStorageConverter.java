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
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.RefundRequestReference;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAllocationPlanDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRequestDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundStorageConverter {
    RefundRequestReference toReference(RefundRequestDO row);

    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "row.rowVersion")
    @Mapping(target = "apply", ignore = true)
    RefundRequest toRequest(RefundRequestDO row, List<RefundOrder> children);

    @Mapping(target = "request", source = "request")
    @Mapping(target = "termsPayload", source = "row.refundTerms")
    StoredRefundRequest toStored(RefundRequestDO row, RefundRequest request, List<StoredRefundChild> allocations);

    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "recordOutcome", ignore = true)
    RefundOrder toRefund(RefundOrderDO row);

    @Mapping(target = "attemptId", source = "originalAttemptId")
    @Mapping(target = "channelRequestNo", source = "originalChannelRequestNo")
    ChannelPaymentIdentity toIdentity(RefundOrderDO row);

    @Mapping(target = "refund", source = "refund")
    @Mapping(target = "original", source = "original")
    @Mapping(target = "channelTransactionNo", source = "row.originalChannelTransactionNo")
    StoredRefundChild toChild(RefundOrderDO row, RefundOrder refund, ChannelPaymentIdentity original, int sequenceNo);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.request.id")
    @Mapping(target = "orderId", source = "value.request.orderId")
    @Mapping(target = "payeeMerchantId", source = "value.request.payeeMerchantId")
    @Mapping(target = "merchantRefundNo", source = "value.request.merchantRefundNo")
    @Mapping(target = "amountMinor", source = "value.request.amount.cent")
    @Mapping(target = "currency", source = "value.request.amount.currencyValue")
    @Mapping(target = "allocationPolicy", constant = "FIFO")
    @Mapping(target = "planVersion", source = "value.request.planVersion")
    @Mapping(target = "state", source = "value.request.state")
    @Mapping(target = "succeededMinor", expression = "java(value.request().succeededMinor())")
    @Mapping(target = "failedMinor", expression = "java(value.request().failedMinor())")
    @Mapping(target = "unknownCount", expression = "java(Math.toIntExact(value.request().unknownCount()))")
    @Mapping(target = "refundTerms", source = "value.termsPayload")
    @Mapping(target = "rowVersion", source = "value.request.version")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    RefundRequestDO toRequestDO(String tenant, StoredRefundRequest value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.refund.id")
    @Mapping(target = "refundRequestId", source = "value.refund.refundRequestId")
    @Mapping(target = "paymentId", source = "value.refund.paymentId")
    @Mapping(target = "originalAttemptId", source = "value.original.attemptId")
    @Mapping(target = "bindingRevisionId", source = "value.original.bindingRevisionId")
    @Mapping(target = "channel", source = "value.original.channel")
    @Mapping(target = "accountScopeHash", source = "value.original.accountScopeHash")
    @Mapping(target = "originalChannelRequestNo", source = "value.original.channelRequestNo")
    @Mapping(target = "originalChannelTransactionNo", source = "value.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.refund.channelRefundNo")
    @Mapping(target = "amountMinor", source = "value.refund.amount.cent")
    @Mapping(target = "currency", source = "value.refund.amount.currencyValue")
    @Mapping(target = "state", source = "value.refund.state")
    @Mapping(target = "terminalEvidenceId", source = "value.refund.terminalEvidenceId")
    @Mapping(target = "rowVersion", source = "value.refund.version")
    @Mapping(target = "channelRefundTransactionNo", ignore = true)
    @Mapping(target = "submissionStartedAt", ignore = true)
    @Mapping(target = "firstUnknownAt", ignore = true)
    @Mapping(target = "nextQueryAt", ignore = true)
    @Mapping(target = "querySequence", constant = "0L")
    @Mapping(target = "succeededAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    RefundOrderDO toChildDO(String tenant, StoredRefundChild value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.refund.id")
    @Mapping(target = "refundRequestId", source = "value.refund.refundRequestId")
    @Mapping(target = "planVersion", constant = "1")
    @Mapping(target = "paymentId", source = "value.refund.paymentId")
    @Mapping(target = "refundId", source = "value.refund.id")
    @Mapping(target = "amountMinor", source = "value.refund.amount.cent")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    RefundAllocationPlanDO toPlanDO(String tenant, StoredRefundChild value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "reservedMinor", source = "reservedMinor")
    @Mapping(target = "rowVersion", source = "version")
    PaymentRefundCapacityDO toReservationPatch(PaymentRefundCapacity value);
}
