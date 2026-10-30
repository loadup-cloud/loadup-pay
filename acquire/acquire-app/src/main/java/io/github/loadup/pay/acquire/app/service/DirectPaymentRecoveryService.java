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
import io.github.loadup.pay.acquire.app.converter.DirectPaymentConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand;
import io.github.loadup.pay.acquire.client.dto.DirectPayRecoveryDTO;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderCreationFacade;
import io.github.loadup.pay.acquire.client.facade.DirectPaymentRecoveryFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentRecoveryFacade;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderCreationRecoveryState;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ADMISSION_EXPIRED;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONTRACT_NOT_ADMITTING;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ORDER_NOT_PAYABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class DirectPaymentRecoveryService implements DirectPaymentRecoveryFacade {
    private final AcquireActorProvider actors;
    private final OrderCreationGateway store;
    private final OrderCreationCodec codec;
    private final AcquireOrderCreationFacade orders;
    private final PaymentRecoveryFacade payments;
    private final DirectPaymentConverter converter;
    private final OrderCreationTransactions transactions;
    private final Validator validator;

    public DirectPaymentRecoveryService(AcquireActorProvider actors, OrderCreationGateway store, OrderCreationCodec codec,
                                        AcquireOrderCreationFacade orders, PaymentRecoveryFacade payments, DirectPaymentConverter converter,
                                        OrderCreationTransactions transactions, Validator validator) {
        this.actors = actors;
        this.store = store;
        this.codec = codec;
        this.orders = orders;
        this.payments = payments;
        this.converter = converter;
        this.transactions = transactions;
        this.validator = validator;
    }

    @Override
    public DirectPayRecoveryDTO recover(DirectPayRecoverCommand command) {
        if (command == null || !validator.validate(command).isEmpty() || command.firstPaymentAmountMinor() > command.amountMinor()
                || !command.interactionKind().supports(command.choice().method())) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var actor = actors.current();
        if (!actor.permissions().containsAll(Set.of("pay:acquire:direct-pay:recover", "pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:recover"))) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        return transactions.withTenantContext(actor.tenantId(), () -> {
            DomainChecks.id(command.payeeMerchantId());
            DomainChecks.id(command.merchantOrderNo());
            var observed = find(actor, command);
            if (observed.isEmpty()) {
                return converter.toRecoveryDTO(command, null, OrderCreationRecoveryState.NOT_OBSERVED, null, null, null);
            }
            var intent = observed.get();
            if (terminalCreation(intent)) {
                return terminal(command, intent);
            }
            io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO created;
            try {
                created = orders.resumeDirectPay(command);
            } catch (CommonException failure) {
                if (failure.getResultCode() == CONTRACT_NOT_ADMITTING || failure.getResultCode() == ADMISSION_EXPIRED
                        || failure.getResultCode() == ACTION_FORBIDDEN || failure.getResultCode() == ORDER_NOT_PAYABLE) {
                    var current = find(actor, command).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    if (terminalCreation(current)) {
                        return terminal(command, current);
                    }
                }
                throw failure;
            }
            if (created == null || !intent.orderId().equals(created.orderId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var payment = payments.recover(converter.toPaymentRecovery(command, created.orderId()));
            if (payment == null || !created.orderId().equals(payment.orderId()) || !command.actionKey().equals(payment.actionKey())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var currentOrder = payment.progress() == null ? created : payment.progress().order();
            if (currentOrder == null || !created.orderId().equals(currentOrder.orderId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return converter.toRecoveryDTO(command, created.orderId(), OrderCreationRecoveryState.COMPLETED, "SUCCESS", currentOrder, payment);
        });
    }

    private Optional<OrderCreateIntent> find(AcquireActor actor, DirectPayRecoverCommand command) {
        var known = store.findIntent(actor.tenantId(), command.payeeMerchantId(), command.merchantOrderNo());
        if (known.isEmpty()) {
            return known;
        }
        var intent = known.get();
        if (!intent.callerScopeHash().equals(actor.scopeHash())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (!"DIRECT_PAY".equals(intent.entryMode()) || !intent.requestDigest().equals(codec.directPayDigest(actor.tenantId(), actor.scopeHash(), command))
                || !codec.decodeDirectPay(intent).equals(command)) {
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        }
        return known;
    }

    private boolean terminalCreation(OrderCreateIntent intent) {
        return "REJECTED".equals(intent.state()) || "EXPIRED".equals(intent.state());
    }

    private DirectPayRecoveryDTO terminal(DirectPayRecoverCommand command, OrderCreateIntent intent) {
        if ("EXPIRED".equals(intent.state()) && ADMISSION_EXPIRED.getCode().equals(intent.resultCode())) {
            return converter.toRecoveryDTO(command, intent.orderId(), OrderCreationRecoveryState.EXPIRED, ADMISSION_EXPIRED.getCode(), null, null);
        }
        if ("REJECTED".equals(intent.state())) {
            for (var reason : java.util.List.of(CONTRACT_NOT_ADMITTING, ACTION_FORBIDDEN, ORDER_NOT_PAYABLE)) {
                if (reason.getCode().equals(intent.resultCode())) {
                    return converter.toRecoveryDTO(command, intent.orderId(), OrderCreationRecoveryState.REJECTED, reason.getCode(), null, null);
                }
            }
        }
        throw new CommonException(SNAPSHOT_INVALID);
    }
}
