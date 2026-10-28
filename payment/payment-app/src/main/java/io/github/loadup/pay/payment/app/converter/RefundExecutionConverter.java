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
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundChannelReceipt;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;
import io.github.loadup.pay.payment.domain.model.StoredRefundChild;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.List;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundExecutionConverter {
    @Mapping(target = "refund", source = "refund")
    StoredRefundChild withRefund(StoredRefundChild current, RefundOrder refund);

    @Mapping(target = "request", source = "request")
    @Mapping(target = "allocations", source = "allocations")
    StoredRefundRequest withRequest(StoredRefundRequest current, RefundRequest request, List<StoredRefundChild> allocations);

    @Mapping(target = "original", source = "child.original")
    @Mapping(target = "refundId", source = "child.refund.id")
    @Mapping(target = "channelRefundNo", source = "child.refund.channelRefundNo")
    @Mapping(target = "amount", source = "child.refund.amount")
    @Mapping(target = "originalAmount", source = "capacity.succeededAmount")
    @Mapping(target = "originalChannelTransactionNo", source = "child.channelTransactionNo")
    PreparedChannelRefund toPrepared(StoredRefundChild child, PaymentRefundCapacity capacity);

    ChannelRefundOutcome toOutcome(RefundObservation observation, String evidenceId);

    RefundChannelReceipt toReceipt(String id, RefundObservation observation, String factsDigest, String observationPayload,
                                   RefundReceiptDisposition disposition, String reasonCode, LocalDateTime receivedAt);

    @Mapping(target = "original", source = "observation.original")
    RefundTransactionClaim toClaim(String id, String tenantId, RefundObservation observation, String receiptId);

    @Mapping(target = "refundId", source = "observation.refundId")
    @Mapping(target = "original", source = "observation.original")
    @Mapping(target = "originalChannelTransactionNo", source = "observation.originalChannelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "observation.channelRefundNo")
    @Mapping(target = "amount", source = "observation.amount")
    @Mapping(target = "acceptedAt", source = "observation.acceptedAt")
    @Mapping(target = "receiptId", source = "receiptId")
    io.github.loadup.pay.payment.domain.model.RefundAcceptanceEvidence toAcceptance(RefundObservation observation, String receiptId);

}
