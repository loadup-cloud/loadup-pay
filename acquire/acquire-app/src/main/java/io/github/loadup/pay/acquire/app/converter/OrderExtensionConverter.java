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
import io.github.loadup.pay.acquire.client.command.AcquireOrderExtendCommand;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.OrderExtensionReceipt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderExtensionConverter {
    @Mapping(target = "id", source = "receiptId")
    @Mapping(target = "orderId", source = "original.id")
    @Mapping(target = "requestKey", source = "command.requestKey")
    @Mapping(target = "expectedOrderVersion", source = "command.expectedVersion")
    @Mapping(target = "previousDueAt", source = "original.orderDueAt")
    @Mapping(target = "requestedDueAt", source = "requestedDueAt")
    OrderExtensionReceipt toReceipt(AcquireOrderExtendCommand command, AcquireOrder original,
                                    String receiptId, String callerScopeHash, String requestDigest, LocalDateTime originalDueAt,
                                    LocalDateTime requestedDueAt, long deadlineVersion, LocalDateTime appliedAt);
}
