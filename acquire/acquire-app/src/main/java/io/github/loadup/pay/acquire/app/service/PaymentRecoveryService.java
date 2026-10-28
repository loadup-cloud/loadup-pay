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
import io.github.loadup.pay.acquire.app.converter.PaymentRecoveryConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.client.command.PaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentProgressDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentRecoveryDTO;
import io.github.loadup.pay.acquire.client.facade.PaymentRecoveryFacade;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class PaymentRecoveryService implements PaymentRecoveryFacade {
    private final AcquireActorProvider actors;
    private final PaymentInitiationOperations initiation;
    private final PaymentOutcomeOperations outcomes;
    private final PaymentRecoveryConverter converter;
    private final Validator validator;

    public PaymentRecoveryService(AcquireActorProvider actors, PaymentInitiationOperations initiation, PaymentOutcomeOperations outcomes,
                                  PaymentRecoveryConverter converter, Validator validator) {
        this.actors = actors;
        this.initiation = initiation;
        this.outcomes = outcomes;
        this.converter = converter;
        this.validator = validator;
    }

    @Override
    public PaymentRecoveryDTO recover(PaymentRecoverCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().contains("pay:acquire:payment:recover")) throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var original = initiation.recover(new PaymentInitiationPrincipal(actor.tenantId(), actor.scopeHash(), "merchant/" + actor.scopeHash(),
                (order, creating) -> {
                    if (!order.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
                    if (creating) throw new CommonException(INVALID_STATE);
                }), command.orderId(), command.actionKey(), command.amountMinor(), command.choice());
        PaymentProgressDTO progress = null;
        if (original.state() == PaymentIntentRecovery.State.RESOLVED) {
            var scope = new OrderPaymentScope(actor.tenantId(), actor.scopeHash());
            progress = original.closed() == null
                    ? outcomes.refresh(scope, new PaymentProgressRefreshCommand(command.orderId(), original.paymentId()))
                    : outcomes.applyInputExpiry(scope, command.orderId(), original.paymentId(), original.closed());
            if (progress == null) throw new CommonException(SNAPSHOT_INVALID);
        }
        return converter.toDTO(command, original, progress);
    }
}
