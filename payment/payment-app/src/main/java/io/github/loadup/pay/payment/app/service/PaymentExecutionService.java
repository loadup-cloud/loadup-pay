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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Durable state decides query versus first submit; a task claim never grants a debit retry. */
@Service
public class PaymentExecutionService {
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentControlGateway changes;
    private final PaymentExecutionLocks locks;
    private final PaymentInitialAttemptService initial;
    private final PaymentSubmissionPreparation submission;
    private final PaymentChannelGateway channels;
    private final PaymentResultService results;
    private final PaymentAcceptanceCodec codec;
    private final PaymentControlConverter updates;
    private final PaymentEvents events;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Clock clock;
    private final io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter conversions;

    public PaymentExecutionService(PaymentAcceptanceGateway payments, PaymentAttemptGateway attempts,
            PaymentControlGateway changes, PaymentExecutionLocks locks, PaymentInitialAttemptService initial,
            PaymentSubmissionPreparation submission, PaymentChannelGateway channels, PaymentResultService results,
            PaymentAcceptanceCodec codec, PaymentControlConverter updates, PaymentEvents events,
            PaymentAcceptanceTransactions transactions, IdGenerator ids, @Qualifier("paymentClock") Clock clock,
            io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter conversions) {
        this.payments = payments; this.attempts = attempts; this.changes = changes; this.locks = locks;
        this.initial = initial; this.submission = submission; this.channels = channels; this.results = results;
        this.codec = codec; this.updates = updates; this.events = events; this.transactions = transactions;
        this.ids = ids; this.clock = clock; this.conversions = conversions;
    }

    public void progress(String tenant, String paymentId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        DomainChecks.id(tenant); DomainChecks.id(paymentId);
        transactions.withTenantContext(tenant, () -> {
            final Optional<Work> pending;
            try { pending = snapshot(tenant, paymentId, false); }
            catch (CommonException rejected) {
                if (!initialRejection(rejected)) throw rejected;
                snapshot(tenant, paymentId, true);
                return true;
            }
            if (pending.isEmpty()) return true;
            var work = pending.get();
            if (work.attempt().attempt().state() == PaymentAttemptState.INIT) {
                final Optional<io.github.loadup.pay.payment.app.model.PaymentSubmissionTicket> ticket;
                try { ticket = submission.prepare(tenant, paymentId, work.attempt().attempt().id()); }
                catch (CommonException rejected) {
                    if (initialRejection(rejected) || rejected.getResultCode() == LIMIT_EXCEEDED
                            || rejected.getResultCode() == LIMIT_RESERVATION_EXPIRED
                            || rejected.getResultCode() == EXECUTION_FENCED
                            || rejected.getResultCode() == io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT
                            || rejected.getResultCode() == io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY
                            || rejected.getResultCode() == io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH) {
                        snapshot(tenant, paymentId, true);
                        return true;
                    }
                    throw rejected;
                }
                if (ticket.isEmpty()) return true;
                // This in-memory Y2 winner is consumed exactly once, even when the response or Y3 commit is lost.
                var prepared = ticket.get().payment();
                var applied = results.recordSubmission(tenant, prepared, channels.submit(prepared));
                if (applied.disposition() == PaymentReceiptDisposition.CONFLICT) throw new CommonException(OUTCOME_UNKNOWN);
            } else {
                var attempt = work.attempt().attempt();
                if (attempt.state() != PaymentAttemptState.SUBMITTING && attempt.state() != PaymentAttemptState.PENDING
                        && attempt.state() != PaymentAttemptState.UNKNOWN) throw new CommonException(SNAPSHOT_INVALID);
                final io.github.loadup.pay.payment.app.model.PaymentResultApplication applied;
                if (work.closing()) {
                    var target = new ChannelPaymentTarget(attempt.identity(), attempt.amount());
                    applied = results.record(tenant, channels.close(target));
                } else {
                    var recovery = conversions.toRecovery(work.attempt(), work.payableUntil());
                    applied = results.recordRecovery(tenant, recovery, channels.recover(recovery));
                }
                if (applied.disposition() == PaymentReceiptDisposition.CONFLICT) throw new CommonException(OUTCOME_UNKNOWN);
            }
            return true;
        });
    }

    private Optional<Work> snapshot(String tenant, String paymentId, boolean rejectUnsubmitted) {
        return transactions.writeLocal(tenant, () -> {
            var found = payments.find(tenant, paymentId).orElseThrow(() -> new CommonException(NOT_FOUND));
            var locked = locks.lock(tenant, paymentId, found.callerServiceId());
            var accepted = locked.payment();
            var order = accepted.payment();
            if (terminal(order.state())) return Optional.empty();
            var stored = order.activeAttemptId() == null ? null : attempts.lock(tenant, order.activeAttemptId())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (stored != null) PaymentExecutionBindings.activeContext(locked, stored);
            boolean neverSubmitted = stored == null ? order.state() == PaymentState.INIT && order.version() == 0
                    : stored.attempt().state() == PaymentAttemptState.INIT && stored.attempt().executionEpoch() == 0;
            // A concurrent Y2 winner turns a local rejection into observation only, never a fabricated no-collection fact.
            if (rejectUnsubmitted && !neverSubmitted) return Optional.empty();
            var payableUntil = codec.decode(accepted).payableUntil();
            boolean closing = order.state() == PaymentState.CLOSING || locked.parent().gate().state() == GateState.FENCED
                    || locked.gate().gate().state() == GateState.FENCED
                    || !clock.instant().isBefore(payableUntil.toInstant());
            if (closing || rejectUnsubmitted) {
                if (stored != null) PaymentExecutionBindings.closableContext(locked, stored);
                var gate = locked.gate();
                if (gate.gate().state() != GateState.FENCED)
                    changes.updateGate(tenant, updates.withGate(gate, gate.gate().fence(Math.max(1, gate.gate().fenceEpoch()))), gate.rowVersion());
                var nextOrder = closing ? order.requestClose() : order;
                String evidence = null;
                if (neverSubmitted) {
                    evidence = ids.generate();
                    if (stored != null) attempts.update(tenant, stored.closeBeforeSubmission(evidence, null), stored.rowVersion());
                } else if (stored != null && stored.attempt().canSafelyReplace()) evidence = stored.attempt().noCollectionEvidenceId();
                if (evidence != null) {
                    var proof = new NoCollectionEvidence(evidence, paymentId, true);
                    nextOrder = closing ? nextOrder.close(proof) : nextOrder.fail(proof);
                } else if (stored == null) throw new CommonException(SNAPSHOT_INVALID);
                var updated = updates.withPayment(accepted, nextOrder, evidence == null ? accepted.terminalEvidenceId() : evidence);
                if (nextOrder.version() != order.version()) {
                    changes.updatePayment(tenant, updated, order.version());
                    if (!terminal(nextOrder.state())) events.publishSnapshot(tenant, updated, now());
                }
                if (terminal(nextOrder.state())) {
                    events.publish(tenant, updated, true, now());
                    return Optional.empty();
                }
                return Optional.of(new Work(stored, true, payableUntil));
            }
            if (stored == null) {
                String requestKey = "initial/" + paymentId;
                // The original complete request digest already binds tenant, caller and frozen payment parameters.
                var updated = initial.create(tenant, accepted, requestKey, codec.checksum("payment-initial:" + accepted.requestDigest()));
                stored = attempts.lock(tenant, updated.payment().activeAttemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                PaymentExecutionBindings.activeContext(PaymentExecutionBindings.locked(updated, locked.parent(), locked.gate()), stored);
            }
            return Optional.of(new Work(stored, false, payableUntil));
        });
    }

    private boolean initialRejection(CommonException rejected) {
        var code = rejected.getResultCode();
        return code == INPUT_EXPIRED || code == PAYMENT_EXPIRED || code == CHANNEL_BINDING_UNAVAILABLE
                || code == ROUTING_PROFILE_UNAVAILABLE || code == UNSUPPORTED_CAPABILITY;
    }

    static boolean terminal(PaymentState state) {
        return state == PaymentState.SUCCEEDED || state == PaymentState.FAILED || state == PaymentState.CLOSED;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }

    private record Work(StoredPaymentAttempt attempt, boolean closing, java.time.OffsetDateTime payableUntil) {
        @Override public String toString() { return io.github.loadup.commons.json.ToStringAsJson.reflectionToString(this); }
    }
}
