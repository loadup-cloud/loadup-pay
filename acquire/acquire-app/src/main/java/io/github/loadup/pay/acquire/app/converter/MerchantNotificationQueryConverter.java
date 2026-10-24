/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.model.MerchantNotificationWakeDecision;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationActor;
import io.github.loadup.pay.acquire.client.command.MerchantNotificationWakeCommand;
import io.github.loadup.pay.acquire.client.dto.MerchantNotificationDTO;
import io.github.loadup.pay.acquire.client.dto.MerchantNotificationWakeDTO;
import io.github.loadup.pay.acquire.client.query.MerchantNotificationPageQuery;
import io.github.loadup.pay.acquire.domain.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface MerchantNotificationQueryConverter {
    @Mapping(target = "maxAttempts", source = "policy.retryMaxAttempts")
    MerchantNotificationDTO toDTO(MerchantNotificationDelivery value);
    MerchantNotificationFilter toFilter(MerchantNotificationPageQuery value);
    MerchantNotificationWakeDTO toDTO(MerchantNotificationWakeReceipt value);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "deliveryId", source = "delivery.id")
    @Mapping(target = "eventId", source = "delivery.eventId")
    @Mapping(target = "requestKey", source = "command.requestKey")
    @Mapping(target = "reason", source = "command.reason")
    @Mapping(target = "principalType", source = "actor.principalType")
    @Mapping(target = "principalId", source = "actor.principalId")
    @Mapping(target = "attemptCount", source = "delivery.attemptCount")
    @Mapping(target = "deliveryVersion", source = "delivery.rowVersion")
    @Mapping(target = "result", source = "decision.result")
    @Mapping(target = "nativeTaskId", source = "decision.nativeTaskId")
    MerchantNotificationWakeReceipt toReceipt(String id, MerchantNotificationWakeCommand command, MerchantNotificationDelivery delivery,
            MerchantNotificationActor actor, MerchantNotificationWakeDecision decision, String callerScopeHash, String requestDigest, LocalDateTime acceptedAt);

    default OffsetDateTime external(LocalDateTime value) { return value == null ? null : BusinessTime.external(value); }
    default LocalDateTime local(OffsetDateTime value) { return value == null ? null : BusinessTime.local(value); }
}
