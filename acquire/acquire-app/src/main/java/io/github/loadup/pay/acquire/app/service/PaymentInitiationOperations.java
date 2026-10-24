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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.app.converter.PaymentInitiationConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentStartDTO;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentInitiationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ORDER_NOT_PAYABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.UNSUPPORTED_CAPABILITY;

@Service
public class PaymentInitiationOperations {
    private final OrderCreationGateway orders;
    private final PaymentInitiationGateway allocations;
    private final OrderCreationCodec ordersCodec;
    private final PaymentInitiationCodec codec;
    private final PaymentAcceptanceClient payment;
    private final PaymentInitiationConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final OrderSnapshotEvents snapshots;

    public PaymentInitiationOperations(
            OrderCreationGateway orders,
            PaymentInitiationGateway allocations,
            OrderCreationCodec ordersCodec,
            PaymentInitiationCodec codec,
            PaymentAcceptanceClient payment,
            PaymentInitiationConverter converter,
            OrderCreationTransactions transactions,
            IdGenerator ids,
            Validator validator,
            @Qualifier("acquireClock") Clock clock, OrderSnapshotEvents snapshots) {
        this.orders = orders;
        this.allocations = allocations;
        this.ordersCodec = ordersCodec;
        this.codec = codec;
        this.payment = payment;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.snapshots = snapshots;
    }

    PaymentStartDTO start(PaymentInitiationPrincipal actor, PaymentStartCommand command) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        validateInteraction(command);
        if (!payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(command.orderId());
            DomainChecks.text(command.actionKey(), 128);
            String actorScope = actor.actionScope();
            StoredPaymentAllocation stored = transactions.write(actor.tenantId(), () -> {
                var order = orders.lockOrder(actor.tenantId(), command.orderId());
                actor.authorize(order, false);
                String digest = digest(actor, actorScope, command, order);
                var prior =
                        allocations.findAction(actor.tenantId(), command.orderId(), actorScope, command.actionKey());
                if (prior.isPresent()) {
                    var value = prior.get();
                    if (!value.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
                    var intent = converter.toIntent(
                            command,
                            value.allocation().id(),
                            value.allocation().paymentId(),
                            actorScope,
                            digest,
                            order.order().amount().getCurrencyValue());
                    value.allocation().requireSameAction(command.orderId(), intent);
                    var frozen = codec.decode(value);
                    var original = ordersCodec.decodeAdmission(order.admissionPayload(), order.payloadHash());
                    if (!frozen.admittedTerms().equals(converter.toAdmitted(order, original)))
                        throw new CommonException(SNAPSHOT_INVALID);
                    if (!frozen.choice().equals(command.choice())) throw new CommonException(IDEMPOTENCY_CONFLICT);
                    if (value.allocation().state() == AllocationState.INIT) throw new CommonException(SNAPSHOT_INVALID);
                    if (value.allocation().state() == AllocationState.HELD
                            && (order.order().paymentReservedMinor()
                            < value.allocation().amount().getCent()
                            || order.order().reservedPaymentCount() < 1))
                        throw new CommonException(SNAPSHOT_INVALID);
                    return value;
                }
                actor.authorize(order, true);
                var initiated = now();
                if (!order.order().canAcceptNewPayment(initiated)) throw new CommonException(ORDER_NOT_PAYABLE);
                var admission = ordersCodec.decodeAdmission(order.admissionPayload(), order.payloadHash());
                var choice = command.choice();
                if (admission.terms().capabilities().stream()
                        .noneMatch(c ->
                                c.channel() == choice.channel() && c.methods().contains(choice.method())))
                    throw new CommonException(UNSUPPORTED_CAPABILITY);
                var intent = converter.toIntent(
                        command,
                        ids.generate(),
                        ids.generate(),
                        actorScope,
                        digest,
                        order.order().amount().getCurrencyValue());
                var change = order.order().reservePayment(intent, initiated);
                var execute = converter.toExecute(
                        order, change.allocation(), command, converter.toAdmitted(order, admission));
                if (!validator.validate(execute).isEmpty()) throw new CommonException(SNAPSHOT_INVALID);
                String payload = codec.encode(execute);
                var allocation = converter.toStored(
                        change.allocation(), actor.scopeHash(), payload, ordersCodec.checksum(payload));
                allocations.reserve(
                        actor.tenantId(),
                        change.order(),
                        allocation,
                        order.order().version());
                snapshots.publish(actor.tenantId(), change.order().id(), change.order().version());
                return allocation;
            });
            // No transaction spans the network. Failure leaves the original hold and exact command recoverable.
            PaymentExecuteCommand execute = codec.decode(stored);
            if (stored.allocation().state() == AllocationState.RELEASED
                    && stored.allocation().paymentSourceVersion() == 0)
                throw new CommonException(ORDER_NOT_PAYABLE);
            if (!(command.interaction() instanceof NoInteractionDTO)) {
                if (stored.allocation().state() != AllocationState.HELD && execute.preparedInputRef() == null)
                    throw new CommonException(SNAPSHOT_INVALID);
                var prepared = payment.prepareInput(actor.tenantId(), converter.toInputPreparation(
                        execute, command.actionKey(), command.interaction()));
                validatePrepared(prepared);
                if (execute.preparedInputRef() == null) {
                    // Only the opaque reference is attached locally. Raw input never enters the order database.
                    var reference = prepared.inputRef();
                    stored = transactions.write(actor.tenantId(), () -> attachInput(actor, command.orderId(), command.actionKey(), reference));
                    execute = codec.decode(stored);
                } else if (!execute.preparedInputRef().equals(prepared.inputRef()))
                    throw new CommonException(SNAPSHOT_INVALID);
            }
            // Preparation may race with a committed close while the network call is in flight.
            if (stored.allocation().state() == AllocationState.RELEASED
                    && stored.allocation().paymentSourceVersion() == 0) throw new CommonException(ORDER_NOT_PAYABLE);
            var result = stored.allocation().state() == AllocationState.HELD
                    ? payment.accept(actor.tenantId(), execute)
                    : payment.detail(actor.tenantId(), stored.allocation().paymentId());
            validateResult(execute, result);
            return converter.toStart(result);
        });
    }

    /** Resume a committed intent using its frozen command, without preparing another input or reserving money. */
    PaymentIntentRecovery recover(PaymentInitiationPrincipal actor, String orderId, String actionKey, long amountMinor, PaymentChoiceDTO choice) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(orderId);
            DomainChecks.text(actionKey, 128);
            if (amountMinor <= 0 || choice == null) throw new CommonException(INVALID_ARGUMENT);
            var known = transactions.write(actor.tenantId(), () -> {
                var order = orders.lockOrder(actor.tenantId(), orderId);
                actor.authorize(order, false);
                var prior = allocations.findAction(actor.tenantId(), orderId, actor.actionScope(), actionKey);
                if (prior.isEmpty()) return prior;
                var stored = prior.get();
                var allocation = stored.allocation();
                var execute = codec.decode(stored);
                if (!stored.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
                if (!allocation.orderId().equals(orderId) || !allocation.actorScope().equals(actor.actionScope())
                        || !allocation.actionKey().equals(actionKey) || allocation.state() == AllocationState.INIT
                        || !execute.orderId().equals(orderId) || !execute.payeeMerchantId().equals(order.order().payeeMerchantId()))
                    throw new CommonException(SNAPSHOT_INVALID);
                if (allocation.amount().getCent() != amountMinor || !execute.choice().equals(choice))
                    throw new CommonException(IDEMPOTENCY_CONFLICT);
                var admission = ordersCodec.decodeAdmission(order.admissionPayload(), order.payloadHash());
                if (!execute.admittedTerms().equals(converter.toAdmitted(order, admission))) throw new CommonException(SNAPSHOT_INVALID);
                if (allocation.state() == AllocationState.HELD && (order.order().paymentReservedMinor() < amountMinor
                        || order.order().reservedPaymentCount() < 1)) throw new CommonException(SNAPSHOT_INVALID);
                return prior;
            });
            if (known.isEmpty()) return new PaymentIntentRecovery(PaymentIntentRecovery.State.NOT_OBSERVED, null);
            var stored = known.get();
            var execute = codec.decode(stored);
            if (stored.allocation().state() == AllocationState.HELD && execute.preparedInputRef() == null) {
                var lookup = payment.lookupInput(actor.tenantId(), converter.toInputLookup(execute, actionKey));
                if (lookup == null || lookup.found() != (lookup.input() != null)) throw new CommonException(SNAPSHOT_INVALID);
                if (!lookup.found()) return new PaymentIntentRecovery(PaymentIntentRecovery.State.INPUT_REQUIRED, null);
                validatePrepared(lookup.input());
                var reference = lookup.input().inputRef();
                // Preserve even an expired original input. Acceptance and execution keep enforcing its original deadline.
                stored = transactions.write(actor.tenantId(), () -> attachInput(actor, orderId, actionKey, reference));
                execute = codec.decode(stored);
            }
            // Released/tombstoned intents are resolved by the existing outcome receipt, never accepted again.
            if (stored.allocation().state() == AllocationState.HELD) {
                if (execute.preparedInputRef() == null)
                    return new PaymentIntentRecovery(PaymentIntentRecovery.State.INPUT_REQUIRED, null);
                // Exact original idempotent acceptance; no current route, interaction or financial identity is substituted.
                var result = payment.accept(actor.tenantId(), execute);
                validateResult(execute, result);
            }
            return new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, stored.allocation().paymentId());
        });
    }

    private StoredPaymentAllocation attachInput(PaymentInitiationPrincipal actor, String orderId, String actionKey,
                                                String inputRef) {
        var order = orders.lockOrder(actor.tenantId(), orderId);
        actor.authorize(order, false);
        var current = allocations.findAction(actor.tenantId(), orderId, actor.actionScope(), actionKey)
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!current.callerScopeHash().equals(actor.scopeHash()) || !current.allocation().orderId().equals(orderId)
                || !current.allocation().actorScope().equals(actor.actionScope()) || !current.allocation().actionKey().equals(actionKey))
            throw new CommonException(SNAPSHOT_INVALID);
        var original = codec.decode(current);
        if (original.preparedInputRef() != null) {
            if (!original.preparedInputRef().equals(inputRef)) throw new CommonException(IDEMPOTENCY_CONFLICT);
            return current;
        }
        if (current.allocation().state() != AllocationState.HELD || current.allocation().paymentSourceVersion() != 0
                || order.order().state() != AcquireOrderState.PENDING) throw new CommonException(ORDER_NOT_PAYABLE);
        String payload = codec.encode(converter.withPreparedInput(original, inputRef));
        var prepared = converter.withPreparedIntent(current, payload, ordersCodec.checksum(payload));
        allocations.attachInput(actor.tenantId(), current, prepared);
        return prepared;
    }

    private void validatePrepared(PaymentInputDTO prepared) {
        if (prepared == null || !validator.validate(prepared).isEmpty() || prepared.inputRef() == null
                || prepared.inputDigest() == null || !prepared.inputDigest().matches("[0-9a-f]{64}")
                || prepared.expiresAt() == null) throw new CommonException(SNAPSHOT_INVALID);
        DomainChecks.id(prepared.inputRef());
    }

    private void validateInteraction(PaymentStartCommand command) {
        boolean valid = switch (command.choice().method()) {
            case H5 -> command.interaction() instanceof NoInteractionDTO
                    || command.interaction() instanceof H5SceneInteractionDTO;
            case PAYMENT_CODE -> command.interaction() instanceof PaymentCodeInteractionDTO;
            case JSAPI -> command.interaction() instanceof ChannelIdentityInteractionDTO identity
                    && identity.channel() == command.choice().channel();
        };
        if (!valid) throw new CommonException(INVALID_ARGUMENT);
    }

    private String digest(PaymentInitiationPrincipal actor, String scope, PaymentStartCommand command, StoredAcquireOrder order) {
        var canonical = new StringBuilder();
        for (String field : List.of(
                "acquire.payment-start.v1",
                actor.tenantId(),
                actor.scopeHash(),
                scope,
                command.orderId(),
                command.actionKey(),
                Long.toString(command.amountMinor()),
                order.order().amount().getCurrencyValue(),
                command.choice().channel().name(),
                command.choice().method().name(),
                switch (command.interaction()) {
                    case NoInteractionDTO ignored -> "NONE";
                    case PaymentCodeInteractionDTO ignored -> "PAYMENT_CODE";
                    case ChannelIdentityInteractionDTO ignored -> "CHANNEL_IDENTITY";
                    case H5SceneInteractionDTO ignored -> "H5_SCENE";
                },
                order.admission().requestDigest()))
            canonical
                    .append(field.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(field);
        return ordersCodec.checksum(canonical.toString());
    }

    private void validateResult(PaymentExecuteCommand execute, PaymentExecutionDTO result) {
        if (result == null
                || !validator.validate(result).isEmpty()
                || result.state() == null
                || result.version() < 0
                || result.initiatedAt() == null
                || !execute.paymentId().equals(result.paymentId())
                || !execute.orderId().equals(result.orderId())
                || !execute.allocationId().equals(result.allocationId())
                || execute.amountMinor() != result.amountMinor()
                || !execute.currency().equals(result.currency())) throw new CommonException(SNAPSHOT_INVALID);
        if (result.state() == PaymentState.INIT
                && (result.activeAttemptId() != null
                || result.action() != null
                || result.terminalEvidenceId() != null
                || result.version() != 0)) throw new CommonException(SNAPSHOT_INVALID);
        if ((result.state() == PaymentState.CLOSED
                || result.state() == PaymentState.FAILED
                || result.state() == PaymentState.SUCCEEDED)
                && result.terminalEvidenceId() == null) throw new CommonException(SNAPSHOT_INVALID);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
