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
import io.github.loadup.pay.acquire.app.converter.DirectPaymentConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.DirectPayCommand;
import io.github.loadup.pay.acquire.client.dto.DirectPayDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderCreationFacade;
import io.github.loadup.pay.acquire.client.facade.DirectPaymentFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentInitiationFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentRecoveryFacade;
import io.github.loadup.pay.acquire.client.enums.PaymentRecoveryState;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

/**
 * Composes durable creation and payment actions; failures retain their original independently committed facts.
 */
@Service
public class DirectPaymentService implements DirectPaymentFacade {
    private final AcquireActorProvider actors;
    private final AcquireOrderCreationFacade orders;
    private final PaymentInitiationFacade payments;
    private final PaymentRecoveryFacade recovery;
    private final DirectPaymentConverter converter;
    private final Validator validator;

    public DirectPaymentService(AcquireActorProvider actors, AcquireOrderCreationFacade orders,
                                PaymentInitiationFacade payments, PaymentRecoveryFacade recovery, DirectPaymentConverter converter, Validator validator) {
        this.actors = actors;
        this.orders = orders;
        this.payments = payments;
        this.recovery = recovery;
        this.converter = converter;
        this.validator = validator;
    }

    @Override
    public DirectPayDTO directPay(DirectPayCommand command) {
        if (command == null || !validator.validate(command).isEmpty()
                || command.firstPaymentAmountMinor() > command.amountMinor())
            throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().containsAll(Set.of("pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:start", "pay:acquire:payment:recover")))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        boolean valid = switch (command.choice().method()) {
            case H5 -> command.interaction() instanceof NoInteractionDTO
                    || command.interaction() instanceof H5SceneInteractionDTO;
            case PAYMENT_CODE -> command.interaction() instanceof PaymentCodeInteractionDTO;
            case JSAPI -> command.interaction() instanceof ChannelIdentityInteractionDTO identity
                    && identity.channel() == command.choice().channel();
        };
        if (!valid) throw new CommonException(INVALID_ARGUMENT);
        var metadata = converter.toRecovery(command);
        var created = orders.createDirectPay(metadata);
        var original = converter.toPaymentRecovery(metadata, created.orderId());
        var resolved = recovery.recover(original);
        if (resolved == null || resolved.state() == null || !created.orderId().equals(resolved.orderId())
                || !command.actionKey().equals(resolved.actionKey()))
            throw new CommonException(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID);
        if (resolved.state() != PaymentRecoveryState.RESOLVED) {
            // Only the explicit original request may prepare input/start A2. Recovery never creates either.
            payments.startPayment(converter.toStart(command, created.orderId()));
            resolved = recovery.recover(original);
        }
        if (resolved == null || resolved.state() != PaymentRecoveryState.RESOLVED || resolved.progress() == null
                || resolved.progress().order() == null || !created.orderId().equals(resolved.orderId())
                || !command.actionKey().equals(resolved.actionKey()))
            throw new CommonException(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID);
        return converter.toDTO(resolved.progress().order(), resolved);
    }
}
