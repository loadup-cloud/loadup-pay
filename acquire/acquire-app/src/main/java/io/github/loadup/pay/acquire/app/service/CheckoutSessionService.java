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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.CheckoutSessionCreateCommand;
import io.github.loadup.pay.acquire.client.dto.CheckoutDTO;
import io.github.loadup.pay.acquire.client.facade.CheckoutSessionFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

@Service
public class CheckoutSessionService implements CheckoutSessionFacade {
    private final AcquireActorProvider actors;
    private final OrderCreationGateway orders;
    private final CheckoutSessionOperations sessions;
    private final OrderCreationTransactions transactions;
    private final Validator validator;

    public CheckoutSessionService(
            AcquireActorProvider actors,
            OrderCreationGateway orders,
            CheckoutSessionOperations sessions,
            OrderCreationTransactions transactions,
            Validator validator) {
        this.actors = actors;
        this.orders = orders;
        this.sessions = sessions;
        this.transactions = transactions;
        this.validator = validator;
    }

    @Override
    public CheckoutDTO create(CheckoutSessionCreateCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().contains("pay:acquire:session")) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(command.orderId());
            DomainChecks.text(command.requestKey(), 128);
            var order = orders.lockOrder(actor.tenantId(), command.orderId());
            if (!actor.scopeHash().equals(order.callerScopeHash())) throw new CommonException(ACTION_FORBIDDEN);
            return sessions.renew(actor, order, command);
        });
    }
}
