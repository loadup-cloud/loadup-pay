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
import io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.payment.client.command.*;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import java.time.LocalDateTime;
import org.mapstruct.*;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderClosureConverter {
    @Mapping(target = "id", source = "intentId")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "payeeMerchantId", source = "order.payeeMerchantId")
    @Mapping(target = "closeEpoch", source = "order.closeEpoch")
    @Mapping(target = "requestKey", source = "command.requestKey")
    @Mapping(target = "reasonCode", source = "command.reasonCode")
    @Mapping(target = "cursorPaymentId", constant = "")
    @Mapping(target = "rowVersion", constant = "0L")
    @Mapping(target = "advanceCursor", ignore = true)
    OrderCloseIntent toIntent(AcquireOrderCloseCommand command, AcquireOrder order, String intentId,
            String callerScopeHash, String requestDigest, LocalDateTime requestedAt);

    @Mapping(target = "requestKey", source = "intent.id", qualifiedByName = "closeRequestKey")
    ParentPaymentFenceCommand toFence(OrderCloseIntent intent);

    @Mapping(target = "paymentId", source = "allocation.allocation.paymentId")
    @Mapping(target = "orderId", source = "intent.orderId")
    @Mapping(target = "payeeMerchantId", source = "intent.payeeMerchantId")
    @Mapping(target = "fenceEpoch", source = "intent.closeEpoch")
    @Mapping(target = "reasonCode", source = "intent.reasonCode")
    @Mapping(target = "requestKey", source = "intent.id", qualifiedByName = "closeRequestKey")
    PaymentCloseCommand toPaymentClose(OrderCloseIntent intent, StoredPaymentAllocation allocation);

    ParentFenceStatusQuery toStatusQuery(OrderCloseIntent intent);

    @Mapping(target = "orderId", source = "intent.orderId")
    @Mapping(target = "closeEpoch", source = "intent.closeEpoch")
    @Mapping(target = "fenced", source = "status.fenced")
    @Mapping(target = "allChildrenConverged", source = "status.allChildrenConverged")
    ParentCloseEvidence toEvidence(OrderCloseIntent intent, ParentFenceDTO status);

    @Named("closeRequestKey")
    default String closeRequestKey(String intentId) {
        return "order-close/" + intentId;
    }
}
