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
package io.github.loadup.pay.acquire.domain.gateway;

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationFilter;

import java.util.Optional;

public interface MerchantNotificationGateway {
    Optional<MerchantNotificationDelivery> find(String tenant, String deliveryId);

    PageResult<MerchantNotificationDelivery> page(String tenant, MerchantNotificationFilter filter);

    Optional<MerchantNotificationDelivery> findEvent(String tenant, String eventId, String targetRef);

    Optional<MerchantNotificationDelivery> lock(String tenant, String deliveryId);

    void insert(String tenant, MerchantNotificationDelivery delivery);

    void update(String tenant, MerchantNotificationDelivery before, MerchantNotificationDelivery after);
}
