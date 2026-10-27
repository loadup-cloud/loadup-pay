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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.pay.payment.client.enums.PaymentInputExpiryDisposition;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.PaymentInputState;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.facade.PaymentControlFacade;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentControlReceipt;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class PaymentControlService implements PaymentControlFacade {
    private final PaymentActorProvider actors;
    private final PaymentAcceptanceGateway gates;
    private final PaymentControlGateway store;
    private final PaymentAttemptGateway attempts;
    private final PaymentInputGateway inputs;
    private final PaymentAcceptanceCodec codec;
    private final PaymentAcceptanceConverter payments;
    private final PaymentControlConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final String acquireService;
    private final PaymentEvents events;
    private final Clock clock;

    public PaymentControlService(
            PaymentActorProvider actors,
            PaymentAcceptanceGateway gates,
            PaymentControlGateway store,
            PaymentAttemptGateway attempts,
            PaymentInputGateway inputs,
            PaymentAcceptanceCodec codec,
            PaymentAcceptanceConverter payments,
            PaymentControlConverter converter,
            PaymentAcceptanceTransactions transactions,
            IdGenerator ids,
            Validator validator,
            PaymentEvents events,
            @Qualifier("paymentClock") Clock clock,
            @Value("${loadup.modules.payment.acquire-service-id:acquire}") String acquireService) {
        this.actors = actors;
        this.gates = gates;
        this.store = store;
        this.attempts = attempts;
        this.inputs = inputs;
        this.codec = codec;
        this.payments = payments;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.events = events;
        this.clock = clock;
        DomainChecks.id(acquireService);
        this.acquireService = acquireService;
    }

    @Override
    public ParentFenceDTO fenceParent(ParentPaymentFenceCommand command) {
        validate(command);
        var actor = actor("pay:payment:fence");
        return transactions.writeLocal(actor.tenantId(), () -> {
            validateIds(command.orderId(), command.payeeMerchantId(), command.requestKey());
            String digest = digest(
                    actor,
                    "PARENT_FENCE",
                    command.orderId(),
                    null,
                    command.payeeMerchantId(),
                    command.requestKey(),
                    command.closeEpoch(),
                    null);
            var prior = store.findReceipt(actor.tenantId(), "PARENT_FENCE", command.orderId(), command.requestKey());
            prior.ifPresent(value -> checked(value, actor, digest));
            var current = parent(actor, command.orderId(), command.payeeMerchantId(), prior.isPresent());
            prior = store.findReceipt(actor.tenantId(), "PARENT_FENCE", command.orderId(), command.requestKey());
            if (prior.isPresent()) {
                checked(prior.get(), actor, digest);
                requireFence(current, prior.get().requestedEpoch());
                return status(actor, current, command.closeEpoch());
            }
            var next = fence(current, command.closeEpoch());
            if (next != current) store.updateParent(actor.tenantId(), next, current.rowVersion());
            store.insertReceipt(
                    actor.tenantId(),
                    new PaymentControlReceipt(
                            ids.generate(),
                            "PARENT_FENCE",
                            command.orderId(),
                            command.orderId(),
                            command.payeeMerchantId(),
                            actor.serviceId(),
                            command.requestKey(),
                            digest,
                            command.closeEpoch(),
                            null,
                            null));
            return status(actor, next, command.closeEpoch());
        });
    }

    @Override
    public ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query) {
        validate(query);
        var actor = actor("pay:payment:fence:read");
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(query.orderId());
            var current = gates.lockParent(actor.tenantId(), query.orderId());
            if (current.isEmpty()) return converter.toParent(query.orderId(), 0, false, false, 0);
            if (!actor.serviceId().equals(current.get().callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
            return status(actor, current.get(), query.closeEpoch());
        });
    }

    @Override
    public PaymentCloseDTO close(PaymentCloseCommand command) {
        validate(command);
        var actor = actor("pay:payment:close");
        return transactions.writeLocal(actor.tenantId(), () -> closeLocked(actor, command));
    }

    private PaymentCloseDTO closeLocked(PaymentActor actor, PaymentCloseCommand command) {
        validateIds(command.orderId(), command.payeeMerchantId(), command.requestKey());
        DomainChecks.id(command.paymentId());
        DomainChecks.text(command.reasonCode(), 64);
        String digest = digest(
                actor,
                "PAYMENT_CLOSE",
                command.orderId(),
                command.paymentId(),
                command.payeeMerchantId(),
                command.requestKey(),
                command.fenceEpoch(),
                command.reasonCode());
        var prior = store.findReceipt(actor.tenantId(), "PAYMENT_CLOSE", command.paymentId(), command.requestKey());
        prior.ifPresent(value -> checked(value, actor, digest));
        var parent = parent(actor, command.orderId(), command.payeeMerchantId(), prior.isPresent());
        var current = gates.lockPayment(actor.tenantId(), command.paymentId());
        if (current.isEmpty() && prior.isPresent()) throw new CommonException(SNAPSHOT_INVALID);
        StoredPaymentGate next;
        if (current.isPresent()) {
            checkBinding(current.get(), actor, command.orderId(), command.payeeMerchantId(), command.paymentId());
            next = current.get();
        } else {
            next = new StoredPaymentGate(
                    ids.generate(),
                    command.payeeMerchantId(),
                    actor.serviceId(),
                    PaymentExecutionGate.init(command.orderId(), command.paymentId())
                            .fence(command.fenceEpoch()),
                    0);
            gates.insertPaymentGate(actor.tenantId(), next);
        }
        prior = store.findReceipt(actor.tenantId(), "PAYMENT_CLOSE", command.paymentId(), command.requestKey());
        var payment = store.lockPayment(actor.tenantId(), command.paymentId());
        payment.ifPresent(value -> checkPayment(value, actor, command));
        payment.ifPresent(value -> PaymentExecutionBindings.locked(value, parent, next));
        if (prior.isPresent()) {
            checked(prior.get(), actor, digest);
            requireFence(next, prior.get().requestedEpoch());
            return response(command, next, payment.orElse(null), prior.get().noCollectionEvidenceId());
        }
        var fenced = fence(next, command.fenceEpoch());
        if (fenced != next) store.updateGate(actor.tenantId(), fenced, next.rowVersion());
        String evidence = null;
        if (payment.isEmpty()) {
            if (current.isPresent()) {
                // A gate without its payment is not sufficient proof of never having collected funds.
                var proof = store.findNoCollectionReceipt(actor.tenantId(), command.paymentId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!proof.orderId().equals(command.orderId())
                        || !proof.payeeMerchantId().equals(command.payeeMerchantId())
                        || !proof.callerServiceId().equals(actor.serviceId()))
                    throw new CommonException(SNAPSHOT_INVALID);
                evidence = proof.noCollectionEvidenceId();
            } else evidence = ids.generate();
        } else {
            var previous = payment.get();
            var order = previous.payment();
            if (order.state() == PaymentState.INIT && order.version() == 0 && order.activeAttemptId() == null) {
                evidence = ids.generate();
                var closed = order.requestClose().close(new NoCollectionEvidence(evidence, order.id(), true));
                var updated = converter.withPayment(previous, closed, evidence);
                store.updatePayment(actor.tenantId(), updated, order.version());
                events.publish(actor.tenantId(), updated, true, now());
                payment = Optional.of(updated);
            } else if (order.state() == PaymentState.INIT
                    || order.state() == PaymentState.PROCESSING
                    || order.state() == PaymentState.UNKNOWN) {
                var unsubmitted = order.activeAttemptId() == null ? Optional.<StoredPaymentAttempt>empty()
                        : attempts.lock(actor.tenantId(), order.activeAttemptId());
                var updatedOrder = order.requestClose();
                if (unsubmitted.isPresent() && unsubmitted.get().attempt().state() == PaymentAttemptState.INIT
                        && unsubmitted.get().attempt().executionEpoch() == 0) {
                    var attempt = unsubmitted.get();
                    PaymentExecutionBindings.closableContext(PaymentExecutionBindings.locked(previous, parent, fenced), attempt);
                    evidence = ids.generate();
                    attempts.update(actor.tenantId(), attempt.closeBeforeSubmission(evidence, null), attempt.rowVersion());
                    updatedOrder = updatedOrder.close(new NoCollectionEvidence(evidence, order.id(), true));
                }
                var updated = converter.withPayment(previous, updatedOrder, evidence == null ? previous.terminalEvidenceId() : evidence);
                store.updatePayment(actor.tenantId(), updated, order.version());
                if (updatedOrder.state() == PaymentState.CLOSED)
                    events.publish(actor.tenantId(), updated, true, now());
                else events.publishSnapshot(actor.tenantId(), updated, now());
                payment = Optional.of(updated);
            } else if (order.state() == PaymentState.CLOSED || order.state() == PaymentState.FAILED)
                evidence = previous.terminalEvidenceId();
        }
        store.insertReceipt(
                actor.tenantId(),
                new PaymentControlReceipt(
                        ids.generate(),
                        "PAYMENT_CLOSE",
                        command.paymentId(),
                        command.orderId(),
                        command.payeeMerchantId(),
                        actor.serviceId(),
                        command.requestKey(),
                        digest,
                        command.fenceEpoch(),
                        command.reasonCode(),
                        evidence));
        return response(command, fenced, payment.orElse(null), evidence);
    }

    private PaymentCloseDTO response(
            PaymentCloseCommand command,
            StoredPaymentGate gate,
            StoredPaymentAcceptance payment,
            String receiptEvidence) {
        String evidence = receiptEvidence;
        if (payment != null) {
            boolean noCollection = payment.payment().state() == PaymentState.CLOSED
                    || payment.payment().state() == PaymentState.FAILED;
            if (receiptEvidence != null && (!noCollection || !receiptEvidence.equals(payment.terminalEvidenceId())))
                throw new CommonException(SNAPSHOT_INVALID);
            evidence = noCollection ? payment.terminalEvidenceId() : null;
        } else if (receiptEvidence == null) throw new CommonException(SNAPSHOT_INVALID);
        return converter.toClose(
                command.paymentId(),
                command.orderId(),
                gate.gate().fenceEpoch(),
                true,
                payment == null ? null : payments.toDTO(payment),
                evidence);
    }

    @Override
    public PaymentInputExpiryDTO expireInput(PaymentInputExpireCommand command) {
        validate(command);
        var actor = actor("pay:payment:input:expire");
        return transactions.writeLocal(actor.tenantId(), () -> {
            DomainChecks.id(command.inputRef()); DomainChecks.id(command.paymentId()); DomainChecks.id(command.orderId());
            DomainChecks.id(command.allocationId()); DomainChecks.id(command.payeeMerchantId()); DomainChecks.text(command.inputKey(), 128);
            DomainChecks.positive(command.amountMinor()); DomainChecks.cnyAmount(command.amountMinor(), command.currency());
            var close = converter.toInputExpiryClose(command);
            var observedReceipt = store.findReceipt(actor.tenantId(), "PAYMENT_CLOSE", command.paymentId(), close.requestKey());
            // Coordinate with acceptance/Y2: parent -> child gate -> payment -> input.
            parent(actor, command.orderId(), command.payeeMerchantId(), observedReceipt.isPresent());
            var gate = gates.lockPayment(actor.tenantId(), command.paymentId());
            gate.ifPresent(value -> checkBinding(value, actor, command.orderId(), command.payeeMerchantId(), command.paymentId()));
            var accepted = store.lockPayment(actor.tenantId(), command.paymentId());
            var input = inputs.lock(actor.tenantId(), command.inputRef())
                    .orElseThrow(() -> new CommonException(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND));
            if (!input.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
            if (!input.id().equals(command.inputRef()) || !input.paymentId().equals(command.paymentId())
                    || !input.orderId().equals(command.orderId()) || !input.allocationId().equals(command.allocationId())
                    || !input.payeeMerchantId().equals(command.payeeMerchantId()) || !input.inputKey().equals(command.inputKey())
                    || input.amount().getCent() != command.amountMinor() || !input.amount().getCurrencyValue().equals(command.currency())
                    || input.channel() != command.choice().channel() || input.method() != command.choice().method())
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            if (accepted.isPresent()) {
                var current = accepted.get();
                checkPayment(current, actor, close);
                var original = codec.decode(current);
                if (gate.isEmpty() || !Objects.equals(original.preparedInputRef(), command.inputRef())
                        || !original.allocationId().equals(command.allocationId()) || original.amountMinor() != command.amountMinor()
                        || !original.currency().equals(command.currency()) || !original.choice().equals(command.choice()))
                    throw new CommonException(SNAPSHOT_INVALID);
                // Input expiry cannot revoke an already accepted payment, including one currently in flight.
                return converter.toInputExpiry(command, PaymentInputExpiryDisposition.PAYMENT_EXISTS, null);
            }
            if (input.state() != PaymentInputState.INIT) throw new CommonException(SNAPSHOT_INVALID);
            var prior = store.findReceipt(actor.tenantId(), "PAYMENT_CLOSE", command.paymentId(), close.requestKey());
            if (!input.expired(now()) && prior.isEmpty())
                return converter.toInputExpiry(command, PaymentInputExpiryDisposition.INPUT_ACTIVE, null);
            // The same deterministic receipt also recovers a committed close whose reply was lost.
            var closed = closeLocked(actor, close);
            if (!closed.fenced() || closed.payment() != null || closed.noCollectionEvidenceId() == null)
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.toInputExpiry(command, PaymentInputExpiryDisposition.EXPIRED_CLOSED, closed);
        });
    }

    private StoredPaymentGate parent(PaymentActor actor, String orderId, String payee, boolean replay) {
        var existing = gates.lockParent(actor.tenantId(), orderId);
        if (existing.isPresent()) {
            checkBinding(existing.get(), actor, orderId, payee, null);
            return existing.get();
        }
        if (replay) throw new CommonException(SNAPSHOT_INVALID);
        var gate = new StoredPaymentGate(
                ids.generate(),
                payee,
                actor.serviceId(),
                PaymentExecutionGate.init(orderId, null).open(),
                0);
        gates.insertParent(actor.tenantId(), gate);
        return gate;
    }

    private StoredPaymentGate fence(StoredPaymentGate current, long epoch) {
        var changed = current.gate().fence(epoch);
        return changed == current.gate() ? current : converter.withGate(current, changed);
    }

    private ParentFenceDTO status(PaymentActor actor, StoredPaymentGate current, long requestedEpoch) {
        var children = store.childrenStatus(actor.tenantId(), current.gate().orderId());
        boolean fenced =
                current.gate().state() == GateState.FENCED && current.gate().fenceEpoch() >= requestedEpoch;
        return converter.toParent(
                current.gate().orderId(),
                current.gate().fenceEpoch(),
                fenced,
                fenced && children.unconvergedCount() == 0,
                Math.toIntExact(children.unknownCount()));
    }

    private void requireFence(StoredPaymentGate gate, long epoch) {
        if (gate.gate().state() != GateState.FENCED || gate.gate().fenceEpoch() < epoch)
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private void checkBinding(StoredPaymentGate gate, PaymentActor actor, String order, String payee, String payment) {
        if (!gate.gate().orderId().equals(order)
                || !Objects.equals(gate.gate().paymentId(), payment)
                || !gate.payeeMerchantId().equals(payee)
                || !gate.callerServiceId().equals(actor.serviceId())) throw new CommonException(IDEMPOTENCY_CONFLICT);
    }

    private void checkPayment(StoredPaymentAcceptance payment, PaymentActor actor, PaymentCloseCommand command) {
        if (!payment.payment().id().equals(command.paymentId())
                || !payment.payment().orderId().equals(command.orderId())
                || !payment.payment().payeeMerchantId().equals(command.payeeMerchantId())
                || !payment.callerServiceId().equals(actor.serviceId()))
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        codec.decode(payment);
    }

    private void checked(PaymentControlReceipt value, PaymentActor actor, String digest) {
        if (!value.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!value.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
    }

    private String digest(
            PaymentActor actor,
            String kind,
            String order,
            String payment,
            String payee,
            String key,
            long epoch,
            String reason) {
        var canonical = new StringBuilder();
        for (String field : Arrays.asList(
                "payment.control.v1",
                actor.tenantId(),
                actor.serviceId(),
                kind,
                order,
                payment,
                payee,
                key,
                Long.toString(epoch),
                reason)) {
            if (field == null) canonical.append("-1:");
            else
                canonical
                        .append(field.getBytes(StandardCharsets.UTF_8).length)
                        .append(':')
                        .append(field);
        }
        return codec.checksum(canonical.toString());
    }

    private PaymentActor actor(String permission) {
        var actor = actors.current();
        if (!actor.serviceId().equals(acquireService) || !actor.permissions().contains(permission))
            throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }

    private void validateIds(String order, String payee, String key) {
        DomainChecks.id(order);
        DomainChecks.id(payee);
        DomainChecks.text(key, 128);
    }
}
