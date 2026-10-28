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
package io.github.loadup.pay.acquire.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.infrastructure.dataobject.CheckoutSessionDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface CheckoutStorageConverter {
    // These one-argument domain transitions are not fluent setters.
    @Mapping(target = "activate", ignore = true)
    @Mapping(target = "expire", ignore = true)
    @Mapping(target = "complete", ignore = true)
    CheckoutSession toSession(CheckoutSessionDO row);

    @Mapping(target = "session", source = "row")
    @Mapping(target = "scope", source = "scope")
    StoredCheckoutSession toStored(CheckoutSessionDO row, CheckoutSessionScope scope);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.session.id")
    @Mapping(target = "orderId", source = "value.session.orderId")
    @Mapping(target = "state", source = "value.session.state")
    @Mapping(target = "expiresAt", source = "value.session.expiresAt")
    @Mapping(target = "scopeSnapshot", source = "payload")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    CheckoutSessionDO toDO(String tenant, StoredCheckoutSession value, String payload);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "revokedAt", source = "revokedAt")
    @Mapping(target = "rowVersion", source = "rowVersion")
    CheckoutSessionDO toRevocationPatch(StoredCheckoutSession value);
}
