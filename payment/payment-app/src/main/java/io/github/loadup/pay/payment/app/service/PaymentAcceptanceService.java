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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.facade.PaymentAcceptanceFacade;
import io.github.loadup.pay.payment.client.facade.PaymentOutcomeFacade;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.UNSUPPORTED_CAPABILITY;

@Service
public class PaymentAcceptanceService implements PaymentAcceptanceFacade, PaymentOutcomeFacade {
    private final PaymentActorProvider actors;
    private final PaymentAcceptanceGateway store;
    private final PaymentInputAccess inputs;
    private final PaymentAcceptanceCodec codec;
    private final PaymentAcceptanceConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String acquireService;

    public PaymentAcceptanceService(
            PaymentActorProvider actors,
            PaymentAcceptanceGateway store,
            PaymentInputAccess inputs,
            PaymentAcceptanceCodec codec,
            PaymentAcceptanceConverter converter,
            PaymentAcceptanceTransactions transactions,
            IdGenerator ids,
            Validator validator,
            @Qualifier("paymentClock") Clock clock,
            @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors;
        this.store = store;
        this.inputs = inputs;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        DomainChecks.id(acquireService);
        this.acquireService = acquireService;
    }

    @Override
    public PaymentExecutionDTO accept(PaymentExecuteCommand command) {
        validate(command);
        var actor = actor("pay:payment:accept");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.read(actor.tenantId(), () -> {
            validateBinding(command);
            return transactions.write(actor.tenantId(), () -> {
                var prior = store.find(actor.tenantId(), command.paymentId());
                if (prior.isPresent()) return replay(actor, command, prior.get());
                // Gate creation races roll back the entire transaction before a fresh retry.
                var parent = store.lockParent(actor.tenantId(), command.orderId());
                if (parent.isPresent()) checkGate(actor, command, parent.get(), null);
                else
                    store.insertParent(
                            actor.tenantId(),
                            new StoredPaymentGate(
                                    ids.generate(),
                                    command.payeeMerchantId(),
                                    actor.serviceId(),
                                    PaymentExecutionGate.init(command.orderId(), null)
                                            .open(),
                                    0));
                var gate = store.lockPayment(actor.tenantId(), command.paymentId());
                if (gate.isPresent()) checkGate(actor, command, gate.get(), command.paymentId());
                else
                    store.insertPaymentGate(
                            actor.tenantId(),
                            new StoredPaymentGate(
                                    ids.generate(),
                                    command.payeeMerchantId(),
                                    actor.serviceId(),
                                    PaymentExecutionGate.init(command.orderId(), command.paymentId())
                                            .open(),
                                    0));
                // A competing payment under this parent may have committed while acquiring the gate.
                prior = store.find(actor.tenantId(), command.paymentId());
                if (prior.isPresent()) return replay(actor, command, prior.get());
                if (store.findAllocation(actor.tenantId(), command.allocationId())
                        .isPresent()) throw new CommonException(IDEMPOTENCY_CONFLICT);
                inputs.requireNewAcceptance(actor.tenantId(), actor.serviceId(), command, now());
                var payment = PaymentOrder.init(
                        command.paymentId(),
                        command.orderId(),
                        command.allocationId(),
                        command.payeeMerchantId(),
                        DomainChecks.amount(command.amountMinor(), command.currency()),
                        ids.generate(),
                        now());
                String payload = codec.encode(command);
                var stored = converter.toStored(
                        payment,
                        command.admissionId(),
                        command.requestDigest(),
                        actor.serviceId(),
                        payload,
                        codec.checksum(payload));
                store.insert(actor.tenantId(), stored);
                return converter.toDTO(stored);
            });
        });
    }

    @Override
    public PaymentExecutionDTO detail(IdQuery query) {
        validate(query);
        var actor = actor("pay:payment:read");
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var stored = store.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!actor.serviceId().equals(stored.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
            codec.decode(stored);
            return converter.toDTO(stored);
        });
    }

    @Override
    public PaymentOutcomeDTO outcome(IdQuery query) {
        validate(query);
        var actor = actor("pay:payment:read");
        return transactions.write(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var initial = store.find(actor.tenantId(), query.id())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!actor.serviceId().equals(initial.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
            var parent = store.lockParent(actor.tenantId(), initial.payment().orderId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var gate = store.lockPayment(actor.tenantId(), query.id())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            // All payment writers hold these gates before changing a payment or its terminal proof.
            var current = store.find(actor.tenantId(), query.id())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var command = codec.decode(current);
            if (!actor.serviceId().equals(current.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
            checkOutcomeGate(actor, command, parent, null);
            checkOutcomeGate(actor, command, gate, query.id());
            return converter.toOutcome(converter.toDTO(current), gate.gate().state() == GateState.FENCED);
        });
    }

    private void checkOutcomeGate(
            PaymentActor actor, PaymentExecuteCommand command, StoredPaymentGate gate, String paymentId) {
        if (!gate.gate().orderId().equals(command.orderId())
                || !java.util.Objects.equals(gate.gate().paymentId(), paymentId)
                || !gate.payeeMerchantId().equals(command.payeeMerchantId())
                || !gate.callerServiceId().equals(actor.serviceId())
                || gate.gate().state() == GateState.INIT) throw new CommonException(SNAPSHOT_INVALID);
    }

    private PaymentExecutionDTO replay(
            PaymentActor actor, PaymentExecuteCommand command, StoredPaymentAcceptance prior) {
        if (!actor.serviceId().equals(prior.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!prior.requestDigest().equals(command.requestDigest())
                || !codec.decode(prior).equals(command)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toDTO(prior);
    }

    private void checkGate(
            PaymentActor actor, PaymentExecuteCommand command, StoredPaymentGate stored, String paymentId) {
        if (!stored.gate().orderId().equals(command.orderId())
                || !java.util.Objects.equals(stored.gate().paymentId(), paymentId)
                || !stored.payeeMerchantId().equals(command.payeeMerchantId())
                || !stored.callerServiceId().equals(actor.serviceId())) throw new CommonException(IDEMPOTENCY_CONFLICT);
        if (stored.gate().state() != GateState.OPEN) throw new CommonException(EXECUTION_FENCED);
    }

    private void validateBinding(PaymentExecuteCommand command) {
        DomainChecks.id(command.paymentId());
        DomainChecks.id(command.orderId());
        DomainChecks.id(command.allocationId());
        DomainChecks.id(command.admissionId());
        DomainChecks.id(command.payeeMerchantId());
        if (command.preparedInputRef() != null) DomainChecks.id(command.preparedInputRef());
        var admitted = command.admittedTerms();
        DomainChecks.id(admitted.contractId());
        if (!command.orderId().equals(admitted.orderId())
                || !command.admissionId().equals(admitted.admissionId())
                || !command.payeeMerchantId().equals(admitted.payeeMerchantId())
                || !command.currency().equals(admitted.currency())
                || command.amountMinor() > admitted.orderAmountMinor()
                || admitted.terms().schemaVersion() != 1) throw new CommonException(INVALID_ARGUMENT);
        var choice = command.choice();
        if (admitted.terms().capabilities().stream()
                .noneMatch(capability -> capability.channel() == choice.channel()
                        && capability.methods().contains(choice.method())))
            throw new CommonException(UNSUPPORTED_CAPABILITY);
    }

    private PaymentActor actor(String permission) {
        var actor = actors.current();
        if (!acquireService.equals(actor.serviceId()) || !actor.permissions().contains(permission))
            throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private void validate(Object command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        var value = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
        if (value.getYear() < 1970 || value.getYear() > 9998) throw new CommonException(INVALID_ARGUMENT);
        return value;
    }
}
