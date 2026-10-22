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
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.app.converter.*;
import io.github.loadup.pay.payment.app.model.PaymentSubmissionTicket;
import io.github.loadup.pay.payment.app.port.*;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.limitcenter.client.dto.*;
import io.github.loadup.pay.limitcenter.client.enums.LimitReservationState;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Internal execution seam: remote preparation first, then one irreversible local Y2 claim. */
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
    private final PaymentResultEvents events;
    public PaymentSubmissionPreparation(PaymentAcceptanceGateway payments, PaymentControlGateway changes,
            PaymentAttemptGateway attempts, PaymentExecutionLocks locks, PaymentInputAccess inputs, PaymentLimitClient limits,
            RiskConsultGateway risk, PaymentAcceptanceCodec codec, PaymentSubmissionConverter converter,
            PaymentControlConverter updates, PaymentAcceptanceTransactions transactions, IdGenerator ids,
            @Qualifier("paymentClock") Clock clock, PaymentResultEvents events) {
        this.payments = payments; this.changes = changes; this.attempts = attempts; this.locks = locks; this.inputs = inputs;
        this.limits = limits; this.risk = risk; this.codec = codec; this.converter = converter; this.updates = updates;
        this.transactions = transactions; this.ids = ids; this.clock = clock;
        this.events = events;
    }
    /** Only the returned in-memory winner may proceed to submit. Missing tickets mean observe/query, never resubmit. */
    public Optional<PaymentSubmissionTicket> prepare(String tenant, String paymentId, String attemptId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!limits.supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        return transactions.read(tenant, () -> {
            DomainChecks.id(paymentId); DomainChecks.id(attemptId);
            var accepted = payments.find(tenant, paymentId).orElseThrow(() -> new CommonException(NOT_FOUND));
            var stored = attempts.find(tenant, attemptId).orElseThrow(() -> new CommonException(NOT_FOUND));
            requireBinding(accepted, stored);
            if (stored.attempt().state() != PaymentAttemptState.INIT || accepted.payment().state() != PaymentState.PROCESSING)
                return Optional.empty();
            var original = codec.decode(accepted);
            var decision = risk.consult(converter.toConsultation(accepted, stored));
            if (decision == null) throw new CommonException(DEPENDENCY_UNAVAILABLE);
            if (decision.decision() == RiskDecisionType.DENY) {
                rejectUnsubmitted(tenant, accepted, attemptId, decision);
                return Optional.empty();
            }
            final LimitExecutionPermitDTO permit;
            try {
                var reservation = limits.reserve(tenant, converter.toReserve(accepted, original.admittedTerms().terms().limits()));
                validateReservation(accepted, original.admittedTerms().terms().limits(), reservation);
                permit = limits.begin(tenant, converter.toBegin(stored));
                validatePermit(accepted, stored, permit);
                var observation = limits.verify(tenant, converter.toVerify(accepted, permit));
                if (observation == null || !observation.valid() || !"VALID".equals(observation.reasonCode()))
                    throw new CommonException(EXECUTION_PERMIT_INVALID);
            } catch (CommonException rejected) {
                if (rejected.getResultCode() == LIMIT_EXCEEDED || rejected.getResultCode() == LIMIT_RESERVATION_EXPIRED
                        || rejected.getResultCode() == EXECUTION_FENCED)
                    rejectUnsubmitted(tenant, accepted, attemptId, decision);
                throw rejected;
            }
            String permitHash = codec.checksum(permit.permit());
            try {
                return transactions.write(tenant, () -> {
                var locked = locks.lock(tenant, paymentId, accepted.callerServiceId());
                var current = attempts.lock(tenant, attemptId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                requireBinding(locked.payment(), current);
                if (current.attempt().state() != PaymentAttemptState.INIT) return Optional.empty();
                locks.requireOpen(locked);
                if (locked.payment().payment().state() != PaymentState.PROCESSING) throw new CommonException(EXECUTION_FENCED);
                inputs.bindToAttempt(tenant, locked.payment(), attemptId, now());
                var started = current.beginSubmission(permit.executionEpoch(), permitHash, decision, now());
                attempts.update(tenant, started, current.rowVersion());
                return Optional.of(new PaymentSubmissionTicket(tenant, paymentId, attemptId, converter.toPrepared(started)));
                });
            } catch (CommonException rejected) {
                if (rejected.getResultCode() == INPUT_EXPIRED) rejectUnsubmitted(tenant, accepted, attemptId, decision);
                throw rejected;
            }
        });
    }
    private void validateReservation(StoredPaymentAcceptance accepted, LimitPolicyRefDTO policy, LimitReservationDTO response) {
        if (response == null || response.reservationId() == null || response.state() == null
                || !accepted.payment().limitOperationId().equals(response.operationId())
                || !accepted.payment().id().equals(response.paymentId()) || !policy.policyId().equals(response.policyId())
                || policy.policyVersion() != response.policyVersion() || response.initiatedAt() == null
                || !BusinessTime.external(accepted.payment().initiatedAt()).equals(response.initiatedAt()) || response.version() < 0)
            throw new CommonException(SNAPSHOT_INVALID);
        DomainChecks.id(response.reservationId());
        if (response.state() == LimitReservationState.EXPIRED) throw new CommonException(LIMIT_RESERVATION_EXPIRED);
        if (response.state() == LimitReservationState.RELEASED) throw new CommonException(EXECUTION_FENCED);
        if (response.state() != LimitReservationState.RESERVED && response.state() != LimitReservationState.EXECUTING)
            throw new CommonException(EXECUTION_PERMIT_INVALID);
    }
    private void validatePermit(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored, LimitExecutionPermitDTO permit) {
        if (permit == null || !accepted.payment().limitOperationId().equals(permit.operationId())
                || !accepted.payment().id().equals(permit.paymentId()) || !stored.attempt().id().equals(permit.attemptId())
                || permit.executionEpoch() < 1 || permit.permit() == null || !permit.permit().matches("[0-9a-f]{64}"))
            throw new CommonException(EXECUTION_PERMIT_INVALID);
    }
    private void requireBinding(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        var original = codec.decode(accepted);
        var attempt = stored.attempt();
        if (!accepted.payment().id().equals(attempt.paymentId()) || !Objects.equals(accepted.payment().activeAttemptId(), attempt.id())
                || !accepted.payment().amount().equals(attempt.amount())
                || !accepted.payment().payeeMerchantId().equals(attempt.binding().payeeMerchantId())
                || !accepted.payment().limitOperationId().equals(stored.limitOperationId())
                || !accepted.callerServiceId().equals(stored.callerServiceId())
                || !accepted.requestDigest().equals(stored.paymentRequestDigest())
                || original.choice().channel() != attempt.binding().channel() || original.choice().method() != attempt.method()
                || !Objects.equals(original.preparedInputRef(), stored.preparedInputRef())) throw new CommonException(SNAPSHOT_INVALID);
    }
    private void rejectUnsubmitted(String tenant, StoredPaymentAcceptance original, String attemptId, RiskDecision decision) {
        transactions.write(tenant, () -> {
            var locked = locks.lock(tenant, original.payment().id(), original.callerServiceId());
            var current = attempts.lock(tenant, attemptId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            requireBinding(locked.payment(), current);
            if (current.attempt().state() != PaymentAttemptState.INIT) return false;
            var order = locked.payment().payment();
            if (order.state() != PaymentState.PROCESSING && order.state() != PaymentState.CLOSING) return false;
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
