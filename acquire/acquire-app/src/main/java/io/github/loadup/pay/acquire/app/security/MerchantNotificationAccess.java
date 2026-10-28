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
package io.github.loadup.pay.acquire.app.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.validation.DomainChecks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;

@Component
public class MerchantNotificationAccess {
    private final MerchantNotificationActorProvider actors;
    private final String operationsService;

    public MerchantNotificationAccess(MerchantNotificationActorProvider actors,
                                      @Value("${loadup.modules.acquire.notification-operations-service-id:pay-ops}") String operationsService) {
        this.actors = actors;
        this.operationsService = DomainChecks.id(operationsService);
    }

    public MerchantNotificationActor reader() {
        var actor = actors.current();
        if (actor.merchant()) {
            if (!actor.permissions().contains("pay:acquire:notification:read"))
                throw new CommonException(ACTION_FORBIDDEN);
            return actor;
        }
        return operator(actor);
    }

    public MerchantNotificationActor operator() {
        return operator(actors.current());
    }

    private MerchantNotificationActor operator(MerchantNotificationActor actor) {
        if (!("OPERATOR".equals(actor.principalType()) || "SERVICE".equals(actor.principalType()) && operationsService.equals(actor.principalId()))
                || !actor.permissions().contains("pay:acquire:notification:manage"))
            throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }
}
