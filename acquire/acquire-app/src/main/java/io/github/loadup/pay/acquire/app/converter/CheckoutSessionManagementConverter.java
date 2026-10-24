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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionRevokeCommand;
import io.github.loadup.pay.acquire.client.dto.CheckoutSessionStatusDTO;
import io.github.loadup.pay.acquire.client.dto.CheckoutSessionRevocationDTO;
import io.github.loadup.pay.acquire.domain.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface CheckoutSessionManagementConverter {
    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "orderId", source = "session.orderId")
    @Mapping(target = "state", source = "session.state")
    @Mapping(target = "expiresAt", source = "session.expiresAt")
    @Mapping(target = "revoked", expression = "java(value.revokedAt() != null)")
    CheckoutSessionStatusDTO toStatus(StoredCheckoutSession value);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "sessionId", source = "after.session.id")
    @Mapping(target = "orderId", source = "after.session.orderId")
    @Mapping(target = "requestKey", source = "command.requestKey")
    @Mapping(target = "reason", source = "command.reason")
    @Mapping(target = "newlyRevoked", expression = "java(before.revokedAt() == null)")
    @Mapping(target = "beforeVersion", source = "before.rowVersion")
    @Mapping(target = "sessionVersion", source = "after.rowVersion")
    @Mapping(target = "revokedAt", source = "after.revokedAt")
    @Mapping(target = "callerScopeHash", source = "callerScopeHash")
    @Mapping(target = "requestDigest", source = "requestDigest")
    CheckoutSessionRevocationReceipt toReceipt(String id, CheckoutSessionRevokeCommand command, StoredCheckoutSession before,
            StoredCheckoutSession after, String callerScopeHash, String requestDigest, LocalDateTime acceptedAt);

    CheckoutSessionRevocationDTO toDTO(CheckoutSessionRevocationReceipt value);
    default OffsetDateTime external(LocalDateTime value) { return value == null ? null : BusinessTime.external(value); }
}
