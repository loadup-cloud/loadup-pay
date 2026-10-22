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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.app.converter.DirectPaymentConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.DirectPayCommand;
import io.github.loadup.pay.acquire.client.dto.DirectPayDTO;
import io.github.loadup.pay.acquire.client.facade.*;
import io.github.loadup.pay.payment.client.dto.*;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

/** Composes durable creation and payment actions; failures retain their original independently committed facts. */
@Service
public class DirectPaymentService implements DirectPaymentFacade {
    private final AcquireActorProvider actors;
    private final AcquireOrderCreationFacade orders;
    private final PaymentInitiationFacade payments;
    private final DirectPaymentConverter converter;
    private final Validator validator;
    public DirectPaymentService(AcquireActorProvider actors, AcquireOrderCreationFacade orders,
            PaymentInitiationFacade payments, DirectPaymentConverter converter, Validator validator) {
        this.actors = actors; this.orders = orders; this.payments = payments;
        this.converter = converter; this.validator = validator;
    }
    @Override public DirectPayDTO directPay(DirectPayCommand command) {
        if (command == null || !validator.validate(command).isEmpty()
                || command.firstPaymentAmountMinor() > command.amountMinor()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().containsAll(Set.of("pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:start")))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        boolean valid = switch (command.choice().method()) {
            case H5 -> command.interaction() instanceof NoInteractionDTO;
            case PAYMENT_CODE -> command.interaction() instanceof PaymentCodeInteractionDTO;
            case JSAPI -> command.interaction() instanceof ChannelIdentityInteractionDTO identity
                    && identity.channel() == command.choice().channel();
        };
        if (!valid) throw new CommonException(INVALID_ARGUMENT);
        var created = orders.create(converter.toCreate(command));
        var payment = payments.startPayment(converter.toStart(command, created.orderId()));
        // Read current order after A2 instead of returning the earlier INIT view.
        var current = orders.detail(new IdQuery(created.orderId()));
        return converter.toDTO(current, payment);
    }
}
