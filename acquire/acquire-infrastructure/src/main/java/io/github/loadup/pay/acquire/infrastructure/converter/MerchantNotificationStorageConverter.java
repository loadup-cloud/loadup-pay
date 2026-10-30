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
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.pay.acquire.infrastructure.dataobject.MerchantNotificationDeliveryDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface MerchantNotificationStorageConverter {
    MerchantNotificationPolicy toPolicy(MerchantNotificationDeliveryDO row);

    @Mapping(target = "policy", source = "policy")
    @Mapping(target = "acknowledge", ignore = true)
    MerchantNotificationDelivery toDomain(MerchantNotificationDeliveryDO row, MerchantNotificationPolicy policy);

    default MerchantNotificationDelivery toDomain(MerchantNotificationDeliveryDO row) {
        return toDomain(row, toPolicy(row));
    }

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "targetRef", source = "value.policy.targetRef")
    @Mapping(target = "signingIdentityRef", source = "value.policy.signingIdentityRef")
    @Mapping(target = "timeoutMillis", source = "value.policy.timeoutMillis")
    @Mapping(target = "retryInitialSeconds", source = "value.policy.retryInitialSeconds")
    @Mapping(target = "retryMultiplier", source = "value.policy.retryMultiplier")
    @Mapping(target = "retryMaximumSeconds", source = "value.policy.retryMaximumSeconds")
    @Mapping(target = "retryMaxAttempts", source = "value.policy.retryMaxAttempts")
    @Mapping(target = "retryWindowSeconds", source = "value.policy.retryWindowSeconds")
    MerchantNotificationDeliveryDO toDO(String tenant, MerchantNotificationDelivery value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nextAttemptAt", source = "nextAttemptAt")
    @Mapping(target = "attemptCount", source = "attemptCount")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "routeFingerprint", source = "routeFingerprint")
    @Mapping(target = "acknowledgedAt", source = "acknowledgedAt")
    @Mapping(target = "rowVersion", source = "rowVersion")
    MerchantNotificationDeliveryDO toPatch(MerchantNotificationDelivery value);
}
