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
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentCaptureSourceConverter {
    @Mapping(target = "ordinal", source = "ordinal")
    @Mapping(target = "kind", constant = "PAYMENT")
    @Mapping(target = "status", constant = "SUCCEEDED")
    @Mapping(target = "channelTransactionNo", source = "claim.channelTransactionNo")
    @Mapping(target = "channelRefundNo", ignore = true)
    @Mapping(target = "merchantPaymentNo", source = "claim.channelRequestNo")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(claim.getAmountMinor(), claim.getCurrency()))")
    @Mapping(target = "sourceAt", source = "claim.succeededAt")
    @Mapping(target = "sourceId", source = "claim.paymentId")
    @Mapping(target = "evidenceId", source = "claim.receiptId")
    @Mapping(target = "sourceVersion", source = "payment.rowVersion")
    PaymentLedgerLine collection(long ordinal, ChannelTransactionClaimDO claim, PaymentOrderDO payment);

    @Mapping(target = "ordinal", source = "ordinal")
    @Mapping(target = "kind", constant = "REFUND")
    @Mapping(target = "status", source = "refund.state")
    @Mapping(target = "channelTransactionNo", source = "accepted.originalChannelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "accepted.channelRefundNo")
    @Mapping(target = "merchantPaymentNo", source = "accepted.channelRequestNo")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(accepted.getAmountMinor(), accepted.getCurrency()))")
    @Mapping(target = "sourceAt", source = "accepted.acceptedAt")
    @Mapping(target = "sourceId", source = "accepted.id")
    @Mapping(target = "evidenceId", source = "accepted.receiptId")
    @Mapping(target = "sourceVersion", source = "refund.rowVersion")
    PaymentLedgerLine refund(long ordinal, RefundAcceptanceDO accepted, RefundOrderDO refund);
}
