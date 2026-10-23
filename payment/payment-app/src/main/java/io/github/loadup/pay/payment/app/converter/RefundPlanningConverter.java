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
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.client.command.RefundCreateCommand;
import io.github.loadup.pay.payment.client.dto.*;
import org.mapstruct.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundPlanningConverter {
    @Mapping(target = "request", source = "request")
    @Mapping(target = "reasonCode", source = "command.reasonCode")
    @Mapping(target = "requestDigest", source = "digest")
    StoredRefundRequest toStored(RefundRequest request, RefundCreateCommand command, String digest, String callerSubjectHash,
            String admissionId, String termsPayload, String termsHash, LocalDateTime plannedAt, List<StoredRefundChild> allocations);

    @Mapping(target = "refund", source = "refund")
    @Mapping(target = "original", source = "claim.identity")
    @Mapping(target = "originalSucceededAt", source = "claim.succeededAt")
    StoredRefundChild toChild(RefundOrder refund, int sequenceNo, ChannelTransactionClaim claim);

    @Mapping(target = "paymentId", source = "refund.paymentId")
    @Mapping(target = "refundId", source = "refund.id")
    @Mapping(target = "amountMinor", source = "refund.amount.cent")
    @Mapping(target = "state", source = "refund.state")
    RefundAllocationDTO toAllocation(StoredRefundChild value);
    List<RefundAllocationDTO> toAllocations(List<StoredRefundChild> values);

    @Mapping(target = "refundRequestId", source = "request.id")
    @Mapping(target = "currency", source = "request.amount.currencyValue")
    @Mapping(target = "amountMinor", source = "request.amount.cent")
    @Mapping(target = "succeededMinor", expression = "java(request.succeededMinor())")
    @Mapping(target = "failedMinor", expression = "java(request.failedMinor())")
    @Mapping(target = "unknownCount", expression = "java(Math.toIntExact(request.unknownCount()))")
    RefundRequestDTO toDTO(RefundRequest request, List<RefundAllocationDTO> allocations);
}
