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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentStartDTO;
import io.github.loadup.pay.acquire.client.facade.PaymentInitiationFacade;
import org.springframework.stereotype.Service;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;

/**
 * Merchant authorization is separate from the shared reservation and acceptance workflow.
 */
@Service
public class PaymentInitiationService implements PaymentInitiationFacade {
    private final AcquireActorProvider actors;
    private final PaymentInitiationOperations operations;

    public PaymentInitiationService(AcquireActorProvider actors, PaymentInitiationOperations operations) {
        this.actors = actors;
        this.operations = operations;
    }

    @Override
    public PaymentStartDTO startPayment(PaymentStartCommand command) {
        var actor = actors.current();
        if (!actor.permissions().contains("pay:acquire:payment:start")) throw new CommonException(ACTION_FORBIDDEN);
        return operations.start(new PaymentInitiationPrincipal(actor.tenantId(), actor.scopeHash(), "merchant/" + actor.scopeHash(),
                (order, creating) -> {
                    if (!order.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
                }), command);
    }
}
