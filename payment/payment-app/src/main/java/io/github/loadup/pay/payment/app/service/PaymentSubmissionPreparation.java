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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter;
import io.github.loadup.pay.payment.app.model.PaymentSubmissionTicket;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.gateway.RiskConsultGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.RiskDecisionType;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_PERMIT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_EXPIRED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_EXCEEDED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.PAYMENT_EXPIRED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Internal execution seam: remote preparation first, then one irreversible local Y2 claim.
 */
@Component
public class PaymentSubmissionPreparation {
    private final PaymentAcceptanceGateway payments;
    private final PaymentControlGateway changes;
    private final PaymentAttemptGateway attempts;
    private final PaymentExecutionLocks locks;
    private final PaymentInputAccess inputs;
    private final PaymentLimitClient limits;
    private final RiskConsultGateway risk;
    private final PaymentAcceptanceCodec codec;
    private final PaymentSubmissionConverter converter;
    private final PaymentControlConverter updates;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Clock clock;
    private final PaymentEvents events;
    private final io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway channels;
    private final io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter channelInputs;

    public PaymentSubmissionPreparation(PaymentAcceptanceGateway payments, PaymentControlGateway changes,
                                        PaymentAttemptGateway attempts, PaymentExecutionLocks locks, PaymentInputAccess inputs, PaymentLimitClient limits,
                                        RiskConsultGateway risk, PaymentAcceptanceCodec codec, PaymentSubmissionConverter converter,
                                        PaymentControlConverter updates, PaymentAcceptanceTransactions transactions, IdGenerator ids,
                                        Clock clock, PaymentEvents events,
                                        io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway channels,
                                        io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter channelInputs) {
        this.payments = payments;
        this.changes = changes;
        this.attempts = attempts;
        this.locks = locks;
        this.inputs = inputs;
        this.limits = limits;
        this.risk = risk;
        this.codec = codec;
        this.converter = converter;
        this.updates = updates;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
        this.events = events;
        this.channels = channels;
        this.channelInputs = channelInputs;
    }

    /**
     * Only the returned in-memory winner may proceed to submit. Missing tickets mean observe/query, never resubmit.
     */
    public Optional<PaymentSubmissionTicket> prepare(String tenant, String paymentId, String attemptId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!limits.supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        return transactions.withTenantContext(tenant, () -> {
            DomainChecks.id(paymentId);
            DomainChecks.id(attemptId);
            var accepted = payments.find(tenant, paymentId).orElseThrow(() -> new CommonException(NOT_FOUND));
            var stored = attempts.find(tenant, attemptId).orElseThrow(() -> new CommonException(NOT_FOUND));
            requireBinding(accepted, stored);
            if (stored.attempt().state() != PaymentAttemptState.INIT || accepted.payment().state() != PaymentState.PROCESSING) {
                return Optional.empty();
            }
            PaymentExecutionBindings.submittable(accepted, stored);
            var original = codec.decode(accepted);
            var decision = risk.consult(converter.toConsultation(accepted, stored));
            if (decision == null) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            if (decision.decision() == RiskDecisionType.DENY) {
                rejectUnsubmitted(tenant, accepted, attemptId, decision);
                return Optional.empty();
            }
            if (!clock.instant().isBefore(original.payableUntil().toInstant())) {
                rejectUnsubmitted(tenant, accepted, attemptId, decision);
                throw new CommonException(PAYMENT_EXPIRED);
            }
            try {
                var interaction = inputs.openForPreparation(tenant, accepted, attemptId);
                channels.validate(channelInputs.toValidation(stored, original, channelInputs.toInput(interaction)));
            } catch (CommonException rejected) {
                var code = rejected.getResultCode();
                if (code == INPUT_EXPIRED || code == io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT
                        || code == io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY
                        || code == io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH) {
                    rejectUnsubmitted(tenant, accepted, attemptId, decision);
                }
                throw rejected;
            }
            final LimitExecutionPermitDTO permit;
            try {
                var reservation = limits.reserve(tenant, converter.toReserve(accepted, original.admittedTerms().terms().limits()));
                validateReservation(accepted, original.admittedTerms().terms().limits(), reservation);
                permit = limits.begin(tenant, converter.toBegin(stored));
                validatePermit(accepted, stored, permit);
                var observation = limits.verify(tenant, converter.toVerify(accepted, permit));
                if (observation == null || !observation.valid() || !"VALID".equals(observation.reasonCode())) {
                    throw new CommonException(EXECUTION_PERMIT_INVALID);
                }
            } catch (CommonException rejected) {
                if (rejected.getResultCode() == LIMIT_EXCEEDED || rejected.getResultCode() == LIMIT_RESERVATION_EXPIRED
                        || rejected.getResultCode() == EXECUTION_FENCED) {
                    rejectUnsubmitted(tenant, accepted, attemptId, decision);
                }
                throw rejected;
            }
            String permitHash = codec.checksum(permit.permit());
            try {
                return transactions.writeLocal(tenant, () -> {
                    var locked = locks.lock(tenant, paymentId, accepted.callerServiceId());
                    var current = attempts.lock(tenant, attemptId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    requireBinding(locked.payment(), current);
                    if (current.attempt().state() != PaymentAttemptState.INIT) {
                        return Optional.empty();
                    }
                    PaymentExecutionBindings.submittableContext(locked, current);
                    if (!clock.instant().isBefore(original.payableUntil().toInstant())) {
                        throw new CommonException(PAYMENT_EXPIRED);
                    }
                    inputs.bindToAttempt(tenant, locked.payment(), attemptId, now());
                    var started = current.beginSubmission(permit.executionEpoch(), permitHash, decision, now());
                    attempts.update(tenant, started, current.rowVersion());
                    return Optional.of(new PaymentSubmissionTicket(tenant, paymentId, attemptId, converter.toPrepared(started)));
                });
            } catch (CommonException rejected) {
                if (rejected.getResultCode() == INPUT_EXPIRED || rejected.getResultCode() == PAYMENT_EXPIRED) {
                    rejectUnsubmitted(tenant, accepted, attemptId, decision);
                }
                throw rejected;
            }
        });
    }

    private void validateReservation(StoredPaymentAcceptance accepted, LimitPolicyRefDTO policy, LimitReservationDTO response) {
        if (response == null || response.reservationId() == null || response.state() == null
                || !accepted.payment().limitOperationId().equals(response.operationId())
                || !accepted.payment().id().equals(response.paymentId()) || !policy.policyId().equals(response.policyId())
                || policy.policyVersion() != response.policyVersion() || response.initiatedAt() == null
                || !BusinessTime.external(accepted.payment().initiatedAt()).equals(response.initiatedAt()) || response.version() < 0) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        DomainChecks.id(response.reservationId());
        if (response.state() == LimitReservationState.EXPIRED) {
            throw new CommonException(LIMIT_RESERVATION_EXPIRED);
        }
        if (response.state() == LimitReservationState.RELEASED) {
            throw new CommonException(EXECUTION_FENCED);
        }
        if (response.state() != LimitReservationState.RESERVED && response.state() != LimitReservationState.EXECUTING) {
            throw new CommonException(EXECUTION_PERMIT_INVALID);
        }
    }

    private void validatePermit(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored, LimitExecutionPermitDTO permit) {
        if (permit == null || !accepted.payment().limitOperationId().equals(permit.operationId())
                || !accepted.payment().id().equals(permit.paymentId()) || !stored.attempt().id().equals(permit.attemptId())
                || permit.executionEpoch() < 1 || permit.permit() == null || !permit.permit().matches("[0-9a-f]{64}")) {
            throw new CommonException(EXECUTION_PERMIT_INVALID);
        }
    }

    private void requireBinding(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        var original = codec.decode(accepted);
        PaymentExecutionBindings.route(PaymentExecutionBindings.active(accepted, stored),
                original.choice().channel(), original.choice().method(), original.preparedInputRef());
    }

    private void rejectUnsubmitted(String tenant, StoredPaymentAcceptance original, String attemptId, RiskDecision decision) {
        transactions.writeLocal(tenant, () -> {
            var locked = locks.lock(tenant, original.payment().id(), original.callerServiceId());
            var current = attempts.lock(tenant, attemptId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            requireBinding(locked.payment(), current);
            if (current.attempt().state() != PaymentAttemptState.INIT) {
                return false;
            }
            var order = locked.payment().payment();
            if (order.state() != PaymentState.PROCESSING && order.state() != PaymentState.CLOSING) {
                return false;
            }
            PaymentExecutionBindings.closableContext(locked, current);
            String evidence = ids.generate();
            var gate = locked.gate();
            if (gate.gate().state() != GateState.FENCED) {
                var fenced = updates.withGate(gate, gate.gate().fence(Math.max(1, gate.gate().fenceEpoch())));
                changes.updateGate(tenant, fenced, gate.rowVersion());
            }
            var closed = current.closeBeforeSubmission(evidence, decision);
            attempts.update(tenant, closed, current.rowVersion());
            var proof = new NoCollectionEvidence(evidence, order.id(), true);
            var failed = order.state() == PaymentState.CLOSING ? order.close(proof) : order.fail(proof);
            changes.updatePayment(tenant, updates.withPayment(locked.payment(), failed, evidence), order.version());
            events.publish(tenant, updates.withPayment(locked.payment(), failed, evidence), true, now());
            return true;
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
