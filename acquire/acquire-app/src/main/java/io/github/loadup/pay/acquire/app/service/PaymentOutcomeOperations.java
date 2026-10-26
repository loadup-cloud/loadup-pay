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
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.PaymentInitiationConverter;
import io.github.loadup.pay.acquire.app.converter.PaymentProgressConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentProgressDTO;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderFeeGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.VerifiedPaymentOutcome;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Shared trusted-fact application; caller authorization remains in each public use case.
 */
@Component
public class PaymentOutcomeOperations {
    private final OrderCreationGateway orders;
    private final PaymentOutcomeGateway allocations;
    private final OrderFeeGateway fees;
    private final PaymentOutcomeClient payment;
    private final PaymentInitiationCodec codec;
    private final OrderCreationCodec orderCodec;
    private final PaymentInitiationConverter initiation;
    private final PaymentProgressConverter converter;
    private final AcquireOrderConverter orderConverter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final OrderSnapshotEvents snapshots;
    private final MerchantNotificationEvents notifications;

    public PaymentOutcomeOperations(OrderCreationGateway orders, PaymentOutcomeGateway allocations, OrderFeeGateway fees,
                                    PaymentOutcomeClient payment, PaymentInitiationCodec codec, OrderCreationCodec orderCodec,
                                    PaymentInitiationConverter initiation, PaymentProgressConverter converter, AcquireOrderConverter orderConverter,
                                    OrderCreationTransactions transactions, IdGenerator ids, Validator validator,
                                    @Qualifier("acquireClock") Clock clock, OrderSnapshotEvents snapshots, MerchantNotificationEvents notifications) {
        this.orders = orders;
        this.allocations = allocations;
        this.fees = fees;
        this.payment = payment;
        this.codec = codec;
        this.orderCodec = orderCodec;
        this.initiation = initiation;
        this.converter = converter;
        this.orderConverter = orderConverter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.snapshots = snapshots;
        this.notifications = notifications;
    }

    PaymentProgressDTO refresh(OrderPaymentScope actor, PaymentProgressRefreshCommand query) {
        requireNoTransaction();
        if (!payment.supportsTenant(actor.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.orderId());
            DomainChecks.id(query.paymentId());
            var order = orders.findOrder(actor.tenantId(), query.orderId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            owned(actor, order);
            var allocation = allocations.findPayment(actor.tenantId(), query.orderId(), query.paymentId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            var command = bound(actor, order, allocation);
            var receipt = allocations.findReceipt(actor.tenantId(), allocation.allocation().id());
            if (receipt.isPresent() && receipt.get().outcome().kind() == VerifiedPaymentOutcome.Kind.NOT_ACCEPTED) {
                try {
                    receipt.get().requireBoundTo(allocation.allocation());
                } catch (DomainViolation corrupt) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                // There is intentionally no payment record to fabricate or query after a tombstone.
                var current = converge(actor, command, receipt.get().outcome());
                return converter.toProgress(null, orderConverter.toDTO(current, now()));
            }
            var response = payment.outcome(actor.tenantId(), query.paymentId());
            var outcome = terminal(command, response);
            var current = converge(actor, command, outcome);
            return converter.toProgress(response.payment(), orderConverter.toDTO(current, now()));
        });
    }

    PaymentProgressDTO applyInputExpiry(OrderPaymentScope actor, String orderId, String paymentId, PaymentCloseDTO response) {
        requireNoTransaction();
        return transactions.read(actor.tenantId(), () -> {
            var order = orders.findOrder(actor.tenantId(), orderId).orElseThrow(() -> new CommonException(NOT_FOUND));
            owned(actor, order);
            var allocation = allocations.findPayment(actor.tenantId(), orderId, paymentId).orElseThrow(() -> new CommonException(NOT_FOUND));
            var command = bound(actor, order, allocation);
            if (response == null || response.payment() != null) throw new CommonException(SNAPSHOT_INVALID);
            applyClose(actor, initiation.toInputExpiryClose(command), response);
            return refresh(actor, new PaymentProgressRefreshCommand(orderId, paymentId));
        });
    }

    void applyClose(OrderPaymentScope actor, PaymentCloseCommand close, PaymentCloseDTO response) {
        requireNoTransaction();
        transactions.read(actor.tenantId(), () -> {
            if (response == null || !validator.validate(response).isEmpty()
                    || !response.paymentId().equals(close.paymentId())
                    || !response.orderId().equals(close.orderId())
                    || response.fenceEpoch() < close.fenceEpoch() || !response.fenced())
                throw new CommonException(SNAPSHOT_INVALID);
            var order = orders.findOrder(actor.tenantId(), close.orderId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            owned(actor, order);
            if (!close.payeeMerchantId().equals(order.order().payeeMerchantId())) throw new CommonException(SNAPSHOT_INVALID);
            var allocation = allocations.findPayment(actor.tenantId(), close.orderId(), close.paymentId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var command = bound(actor, order, allocation);
            VerifiedPaymentOutcome outcome;
            if (response.payment() == null) {
                if (response.noCollectionEvidenceId() == null || response.noCollectionEvidenceId().isBlank())
                    throw new CommonException(SNAPSHOT_INVALID);
                outcome = converter.toNotAccepted(command, response.noCollectionEvidenceId());
            } else {
                outcome = terminal(command, converter.toObserved(response.payment(), response.fenced()));
                String evidence = response.noCollectionEvidenceId();
                boolean noCollection = outcome != null && outcome.kind() == VerifiedPaymentOutcome.Kind.NO_COLLECTION;
                if (noCollection ? !outcome.evidenceId().equals(evidence) : evidence != null)
                    throw new CommonException(SNAPSHOT_INVALID);
            }
            return converge(actor, command, outcome);
        });
    }

    PaymentExecuteCommand bound(OrderPaymentScope actor, StoredAcquireOrder order, StoredPaymentAllocation stored) {
        if (!stored.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
        var command = codec.decode(stored);
        var original = orderCodec.decodeAdmission(order.admissionPayload(), order.payloadHash());
        if (!(stored.allocation().actorScope().equals("merchant/" + actor.scopeHash())
                || stored.allocation().actorScope().matches("checkout/[A-Za-z0-9_-]{1,64}"))
                || !order.order().id().equals(command.orderId())
                || !order.order().payeeMerchantId().equals(command.payeeMerchantId())
                || !command.admittedTerms().equals(initiation.toAdmitted(order, original)))
            throw new CommonException(SNAPSHOT_INVALID);
        return command;
    }

    private StoredAcquireOrder converge(OrderPaymentScope actor, PaymentExecuteCommand command, VerifiedPaymentOutcome outcome) {
        return transactions.write(actor.tenantId(), () -> convergeLocked(actor, command, outcome));
    }

    StoredAcquireOrder convergeLocked(OrderPaymentScope actor, PaymentExecuteCommand command, VerifiedPaymentOutcome outcome) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var currentOrder = orders.lockOrder(actor.tenantId(), command.orderId());
        owned(actor, currentOrder);
        var current = allocations.lockPayment(actor.tenantId(), command.orderId(), command.paymentId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!command.equals(bound(actor, currentOrder, current))) throw new CommonException(SNAPSHOT_INVALID);
        var receipt = allocations.findReceipt(actor.tenantId(), current.allocation().id());
        if (receipt.isPresent()) {
            try {
                receipt.get().requireBoundTo(current.allocation());
                if (outcome == null) throw new CommonException(SNAPSHOT_INVALID);
                receipt.get().requireSameOutcome(outcome);
            } catch (DomainViolation corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return currentOrder;
        }
        if (current.allocation().state() != AllocationState.HELD
                || currentOrder.order().paymentReservedMinor() < current.allocation().amount().getCent()
                || currentOrder.order().reservedPaymentCount() < 1) throw new CommonException(SNAPSHOT_INVALID);
        if (outcome == null) return currentOrder;
        if (outcome.kind() == VerifiedPaymentOutcome.Kind.NOT_ACCEPTED
                ? current.allocation().paymentSourceVersion() != 0
                : outcome.paymentVersion() <= current.allocation().paymentSourceVersion())
            throw new CommonException(SNAPSHOT_INVALID);
        var change = currentOrder.order().applyPaymentOutcome(current.allocation(), outcome);
        var accepted = converter.toReceipt(ids.generate(), current.allocation().id(), outcome, now());
        allocations.apply(actor.tenantId(), change, current, accepted, currentOrder.order().version());
        if (change.order().state() == AcquireOrderState.PAID) {
            var admission = orderCodec.decodeAdmission(currentOrder.admissionPayload(), currentOrder.payloadHash());
            fees.confirmPaid(actor.tenantId(), change.order(), orderConverter.toFeePolicy(admission.terms().fee()), accepted.observedAt());
        }
        snapshots.publish(actor.tenantId(), change.order().id(), change.order().version());
        notifications.payment(actor.tenantId(), change.order().id(), outcome);
        return converter.withOrder(currentOrder, change.order());
    }

    VerifiedPaymentOutcome terminal(PaymentExecuteCommand command, PaymentOutcomeDTO response) {
        if (response == null || !validator.validate(response).isEmpty()) throw new CommonException(SNAPSHOT_INVALID);
        var value = response.payment();
        if (value == null || value.state() == null || value.version() < 0 || value.initiatedAt() == null
                || !command.paymentId().equals(value.paymentId()) || !command.orderId().equals(value.orderId())
                || !command.allocationId().equals(value.allocationId()) || command.amountMinor() != value.amountMinor()
                || !command.currency().equals(value.currency())) throw new CommonException(SNAPSHOT_INVALID);
        boolean succeeded = value.state() == PaymentState.SUCCEEDED;
        boolean noCollection = value.state() == PaymentState.FAILED || value.state() == PaymentState.CLOSED;
        if (!succeeded && !noCollection) {
            if (value.terminalEvidenceId() != null
                    || value.state() == PaymentState.INIT && (value.version() != 0 || value.activeAttemptId() != null || value.action() != null))
                throw new CommonException(SNAPSHOT_INVALID);
            return null;
        }
        if (value.version() <= 0 || value.action() != null || value.terminalEvidenceId() == null
                || value.terminalEvidenceId().isBlank()
                || succeeded && (value.activeAttemptId() == null || value.activeAttemptId().isBlank())
                || noCollection && !response.executionFenced()) throw new CommonException(SNAPSHOT_INVALID);
        return converter.toOutcome(value,
                succeeded ? VerifiedPaymentOutcome.Kind.SUCCEEDED : VerifiedPaymentOutcome.Kind.NO_COLLECTION, noCollection);
    }

    private void owned(OrderPaymentScope actor, StoredAcquireOrder order) {
        if (!order.callerScopeHash().equals(actor.scopeHash())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
