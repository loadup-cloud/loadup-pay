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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.app.converter.CashierPaymentConverter;
import io.github.loadup.pay.acquire.app.port.CashierInteractionProvider;
import io.github.loadup.pay.acquire.app.security.CashierActorProvider;
import io.github.loadup.pay.acquire.client.command.CashierPaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.command.CashierPaymentStartCommand;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.client.dto.CashierOrderDTO;
import io.github.loadup.pay.acquire.client.dto.CashierPaymentDTO;
import io.github.loadup.pay.acquire.client.dto.CashierPaymentRecoveryDTO;
import io.github.loadup.pay.acquire.client.facade.CashierFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.UNSUPPORTED_CAPABILITY;

@Service
public class CashierService implements CashierFacade {
    private final CashierActorProvider actors;
    private final CashierAccess access;
    private final CashierChoices choices;
    private final CashierInteractionProvider inputs;
    private final OrderCreationGateway orders;
    private final PaymentOutcomeGateway allocations;
    private final PaymentInitiationOperations initiation;
    private final PaymentOutcomeOperations outcomes;
    private final CashierSummaryService summaries;
    private final CashierPaymentConverter converter;
    private final OrderCreationTransactions transactions;
    private final Validator validator;
    private final Clock clock;

    public CashierService(CashierActorProvider actors, CashierAccess access, CashierChoices choices, CashierInteractionProvider inputs,
                          OrderCreationGateway orders, PaymentOutcomeGateway allocations, PaymentInitiationOperations initiation, PaymentOutcomeOperations outcomes,
                          CashierSummaryService summaries, CashierPaymentConverter converter, OrderCreationTransactions transactions, Validator validator,
                          Clock clock) {
        this.actors = actors;
        this.access = access;
        this.choices = choices;
        this.inputs = inputs;
        this.orders = orders;
        this.allocations = allocations;
        this.initiation = initiation;
        this.outcomes = outcomes;
        this.summaries = summaries;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public CashierOrderDTO summary(IdQuery query) {
        return summaries.summary(query);
    }

    @Override
    public CashierPaymentDTO startPayment(CashierPaymentStartCommand command) {
        validate(command);
        noTransaction();
        var actor = actors.current();
        if (!actor.orderId().equals(command.orderId())) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
        String owner = transactions.withTenantContext(actor.tenantId(), () -> {
            var order = orders.findOrder(actor.tenantId(), actor.orderId()).orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            access.read(actor, order, "PAYMENT_START");
            return order.callerScopeHash();
        });
        var interaction = inputs.prepare(actor, command.choice());
        var result = initiation.start(new PaymentInitiationPrincipal(actor.tenantId(), owner, "checkout/" + actor.sessionId(), (order, creating) -> {
            access.authorize(actor, owner, order, creating);
            if (creating && !choices.choices(order, LocalDateTime.ofInstant(clock.instant(), clock.getZone())).contains(command.choice())) {
                throw new CommonException(UNSUPPORTED_CAPABILITY);
            }
        }), converter.toStart(command, interaction));
        return converter.toCashier(result, summary(new IdQuery(actor.orderId())));
    }

    @Override
    public CashierPaymentDTO paymentProgress(IdQuery query) {
        validate(query);
        noTransaction();
        var actor = actors.current();
        String owner = transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var order = orders.findOrder(actor.tenantId(), actor.orderId()).orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            access.read(actor, order, "PAYMENT_PROGRESS");
            var allocation = allocations.findPayment(actor.tenantId(), actor.orderId(), query.id()).orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            if (!allocation.allocation().actorScope().equals("checkout/" + actor.sessionId())
                    || !allocation.callerScopeHash().equals(order.callerScopeHash())) {
                throw new CommonException(SESSION_UNAUTHORIZED);
            }
            return order.callerScopeHash();
        });
        var progress = outcomes.refresh(new OrderPaymentScope(actor.tenantId(), owner), new PaymentProgressRefreshCommand(actor.orderId(), query.id()));
        return converter.toCashier(progress, query.id(), summary(new IdQuery(actor.orderId())));
    }

    @Override
    public CashierPaymentRecoveryDTO recoverPayment(CashierPaymentRecoverCommand command) {
        validate(command);
        noTransaction();
        var actor = actors.current();
        if (!actor.orderId().equals(command.orderId())) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
        var owner = transactions.withTenantContext(actor.tenantId(), () -> {
            var order = orders.findOrder(actor.tenantId(), actor.orderId()).orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            access.read(actor, order, "PAYMENT_PROGRESS");
            return order.callerScopeHash();
        });
        var original = initiation.recover(new PaymentInitiationPrincipal(actor.tenantId(), owner, "checkout/" + actor.sessionId(),
                        (order, creating) -> access.authorize(actor, owner, order, false)), command.orderId(), command.actionKey(),
                command.amountMinor(), command.choice());
        CashierPaymentDTO result = null;
        if (original.state() == PaymentIntentRecovery.State.RESOLVED) {
            var scope = new OrderPaymentScope(actor.tenantId(), owner);
            var progress = original.closed() == null ? outcomes.refresh(scope, new PaymentProgressRefreshCommand(actor.orderId(), original.paymentId()))
                    : outcomes.applyInputExpiry(scope, actor.orderId(), original.paymentId(), original.closed());
            result = converter.toCashier(progress, original.paymentId(), summary(new IdQuery(actor.orderId())));
        }
        return converter.toRecovery(command, converter.toRecoveryState(original.state()), result);
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private void noTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
