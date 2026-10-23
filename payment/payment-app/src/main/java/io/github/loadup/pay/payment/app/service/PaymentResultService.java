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
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.converter.PaymentResultConverter;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.model.PaymentResultApplication;
import io.github.loadup.pay.payment.app.port.PaymentResultCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentObservation;
import io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition;
import io.github.loadup.pay.payment.domain.model.PaymentResultAnomaly;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.APPLIED;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.CONFLICT;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.IGNORED;

/**
 * Y3 internal use case. Call only with authenticated, account-verified adapter facts.
 * No merchant/operations HTTP endpoint accepts an observation or an evidence id.
 */
@Component
public class PaymentResultService {
    private final PaymentAcceptanceGateway payments;
    private final PaymentControlGateway changes;
    private final PaymentAttemptGateway attempts;
    private final PaymentResultGateway results;
    private final PaymentExecutionLocks locks;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentResultCodec codec;
    private final PaymentResultConverter converter;
    private final PaymentControlConverter updates;
    private final PaymentAcceptanceConverter views;
    private final PaymentResultEvents events;
    private final IdGenerator ids;
    private final Clock clock;

    public PaymentResultService(PaymentAcceptanceGateway payments, PaymentControlGateway changes,
                                PaymentAttemptGateway attempts, PaymentResultGateway results, PaymentExecutionLocks locks,
                                PaymentAcceptanceTransactions transactions, PaymentResultCodec codec, PaymentResultConverter converter,
                                PaymentControlConverter updates, PaymentAcceptanceConverter views, PaymentResultEvents events,
                                IdGenerator ids, @Qualifier("paymentClock") Clock clock) {
        this.payments = payments;
        this.changes = changes;
        this.attempts = attempts;
        this.results = results;
        this.locks = locks;
        this.transactions = transactions;
        this.codec = codec;
        this.converter = converter;
        this.updates = updates;
        this.views = views;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    public PaymentResultApplication record(String tenant, PaymentObservation observation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (observation == null) throw new CommonException(INVALID_ARGUMENT);
        return transactions.read(tenant, () -> {
            DomainChecks.id(tenant);
            String digest = codec.factsDigest(observation);
            String payload = codec.encode(observation);
            var original = payments.find(tenant, observation.identity().paymentId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            return transactions.write(tenant, () -> apply(tenant, original.callerServiceId(), observation, digest, payload));
        });
    }

    private PaymentResultApplication apply(String tenant, String caller, PaymentObservation observation, String digest, String payload) {
        var locked = locks.lock(tenant, observation.identity().paymentId(), caller);
        var accepted = locked.payment();
        var prior = results.receipt(tenant, observation.identity().attemptId(), observation.source(), observation.receiptKey());
        if (prior.isPresent()) {
            var receipt = prior.get();
            if (!receipt.factsDigest().equals(digest))
                return anomaly(tenant, accepted, receipt.id(), observation, digest, payload, "RECEIPT_FACTS_CONFLICT");
            return present(receipt.disposition(), receipt.id(), accepted);
        }
        String receiptId = ids.generate();
        var attempt = attempts.lock(tenant, observation.identity().attemptId())
                .orElseThrow(() -> new CommonException(NOT_FOUND));
        String reason = mismatch(accepted, attempt, observation);
        if (reason != null) return reject(tenant, accepted, receiptId, observation, digest, payload, reason);
        if (observation.kind() == ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED)
            return reject(tenant, accepted, receiptId, observation, digest, payload, "FUNDS_RETURNED_REQUIRES_COMPENSATION");
        if (observation.kind() == ChannelPaymentOutcome.Kind.SUCCEEDED)
            return success(tenant, locked, attempt, receiptId, observation, digest, payload);
        if (observation.kind() == ChannelPaymentOutcome.Kind.NO_COLLECTION)
            return noCollection(tenant, locked, attempt, receiptId, observation, digest, payload);
        // Intermediate observations cannot undo either a positive or a negative terminal fact.
        if (terminal(accepted.payment().state()) || attempt.attempt().canSafelyReplace()
                || attempt.attempt().state() == PaymentAttemptState.SUCCEEDED)
            return receipt(tenant, accepted, receiptId, observation, digest, payload, IGNORED, null);
        var updated = attempt.applyOutcome(outcome(observation, receiptId), false);
        var order = accepted.payment().applyAttempt(updated.attempt());
        if (!updated.equals(attempt)) attempts.update(tenant, updated, attempt.rowVersion());
        var changed = updates.withPayment(accepted, order, accepted.terminalEvidenceId());
        if (order.version() != accepted.payment().version())
            changes.updatePayment(tenant, changed, accepted.payment().version());
        return receipt(tenant, changed, receiptId, observation, digest, payload,
                updated.equals(attempt) ? IGNORED : APPLIED, null);
    }

    private PaymentResultApplication success(String tenant, LockedPayment locked, StoredPaymentAttempt attempt,
                                             String id, PaymentObservation observation, String digest, String payload) {
        var accepted = locked.payment();
        if (accepted.payment().state() == PaymentState.FAILED || accepted.payment().state() == PaymentState.CLOSED
                || attempt.attempt().canSafelyReplace())
            return reject(tenant, accepted, id, observation, digest, payload, "TERMINAL_FACT_CONFLICT");
        var claimed = results.transaction(observation.identity().channel(), observation.identity().accountScopeHash(),
                observation.channelTransactionNo());
        if (claimed.isPresent() && !matches(tenant, claimed.get(), observation))
            return reject(tenant, accepted, id, observation, digest, payload, "CHANNEL_TRANSACTION_ALREADY_CLAIMED");
        var own = results.attemptClaim(tenant, observation.identity().attemptId());
        if (own.isPresent() && !matches(tenant, own.get(), observation))
            return reject(tenant, accepted, id, observation, digest, payload, "ATTEMPT_TRANSACTION_CONFLICT");
        if (accepted.payment().state() == PaymentState.SUCCEEDED) {
            if (attempt.attempt().state() != PaymentAttemptState.SUCCEEDED || own.isEmpty() || claimed.isEmpty()
                    || !own.get().id().equals(claimed.get().id())
                    || !Objects.equals(accepted.terminalEvidenceId(), own.get().receiptId()))
                throw new CommonException(SNAPSHOT_INVALID);
            requireCapacity(tenant, accepted, observation.succeededAt());
            return receipt(tenant, accepted, id, observation, digest, payload, IGNORED, null);
        }
        if (claimed.isPresent() || own.isPresent() || results.capacity(tenant, accepted.payment().id()).isPresent())
            throw new CommonException(SNAPSHOT_INVALID);
        var updated = attempt.applyOutcome(outcome(observation, id), false);
        var succeeded = updates.withPayment(accepted, accepted.payment().applyAttempt(updated.attempt()), id);
        results.insertClaim(converter.toClaim(ids.generate(), tenant, observation, id));
        results.insertCapacity(tenant, ids.generate(), converter.toCapacity(accepted, observation.succeededAt()));
        attempts.update(tenant, updated, attempt.rowVersion());
        results.completeSuccess(tenant, succeeded, accepted.payment().version(), observation.succeededAt());
        var applied = receipt(tenant, succeeded, id, observation, digest, payload, APPLIED, null);
        events.publish(tenant, succeeded, locked.gate().gate().state() == GateState.FENCED, now());
        return applied;
    }

    private PaymentResultApplication noCollection(String tenant, LockedPayment locked, StoredPaymentAttempt attempt,
                                                  String id, PaymentObservation observation, String digest, String payload) {
        var accepted = locked.payment();
        if (accepted.payment().state() == PaymentState.SUCCEEDED || attempt.attempt().state() == PaymentAttemptState.SUCCEEDED)
            return reject(tenant, accepted, id, observation, digest, payload, "TERMINAL_FACT_CONFLICT");
        if (attempt.attempt().canSafelyReplace() && terminal(accepted.payment().state()))
            return receipt(tenant, accepted, id, observation, digest, payload, IGNORED, null);
        if (attempt.attempt().attemptNo() != 1)
            return reject(tenant, accepted, id, observation, digest, payload, "PAYMENT_WIDE_PROOF_REQUIRED");
        if (terminal(accepted.payment().state())) throw new CommonException(SNAPSHOT_INVALID);
        var gate = locked.gate();
        if (gate.gate().state() != GateState.FENCED)
            changes.updateGate(tenant, updates.withGate(gate, gate.gate().fence(Math.max(1, gate.gate().fenceEpoch()))), gate.rowVersion());
        var updated = attempt.applyOutcome(outcome(observation, id), true);
        var proof = new NoCollectionEvidence(id, accepted.payment().id(), true);
        var order = accepted.payment().state() == PaymentState.CLOSING
                ? accepted.payment().close(proof) : accepted.payment().fail(proof);
        var failed = updates.withPayment(accepted, order, id);
        if (!updated.equals(attempt)) attempts.update(tenant, updated, attempt.rowVersion());
        changes.updatePayment(tenant, failed, accepted.payment().version());
        var applied = receipt(tenant, failed, id, observation, digest, payload, APPLIED, null);
        events.publish(tenant, failed, true, now());
        return applied;
    }

    private String mismatch(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored, PaymentObservation observation) {
        var attempt = stored.attempt();
        if (!attempt.identity().equals(observation.identity())
                || !Objects.equals(accepted.payment().activeAttemptId(), attempt.id())
                || !accepted.payment().id().equals(attempt.paymentId())) return "PAYMENT_IDENTITY_MISMATCH";
        if (!accepted.payment().payeeMerchantId().equals(attempt.binding().payeeMerchantId())
                || !accepted.payment().amount().equals(attempt.amount())
                || !accepted.requestDigest().equals(stored.paymentRequestDigest())
                || !accepted.payment().limitOperationId().equals(stored.limitOperationId())
                || !accepted.callerServiceId().equals(stored.callerServiceId()))
            throw new CommonException(SNAPSHOT_INVALID);
        if (!attempt.amount().equals(observation.amount())) return "PAYMENT_AMOUNT_MISMATCH";
        if (attempt.executionEpoch() == 0) return "OBSERVATION_BEFORE_SUBMISSION";
        return null;
    }

    private boolean matches(String tenant, ChannelTransactionClaim claim, PaymentObservation observation) {
        return claim.tenantId().equals(tenant) && claim.identity().equals(observation.identity())
                && claim.amount().equals(observation.amount()) && claim.channelTransactionNo().equals(observation.channelTransactionNo())
                && claim.succeededAt().equals(observation.succeededAt());
    }

    private void requireCapacity(String tenant, StoredPaymentAcceptance accepted, LocalDateTime succeededAt) {
        var capacity = results.capacity(tenant, accepted.payment().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!capacity.orderId().equals(accepted.payment().orderId())
                || !capacity.payeeMerchantId().equals(accepted.payment().payeeMerchantId())
                || !capacity.succeededAmount().equals(accepted.payment().amount()) || !capacity.succeededAt().equals(succeededAt))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private ChannelPaymentOutcome outcome(PaymentObservation observation, String evidenceId) {
        return new ChannelPaymentOutcome(observation.kind(), observation.identity(), observation.amount(), evidenceId);
    }

    private PaymentResultApplication reject(String tenant, StoredPaymentAcceptance accepted, String id,
                                            PaymentObservation observation, String digest, String payload, String reason) {
        receipt(tenant, accepted, id, observation, digest, payload, CONFLICT, reason);
        return anomaly(tenant, accepted, id, observation, digest, payload, reason);
    }

    private PaymentResultApplication anomaly(String tenant, StoredPaymentAcceptance accepted, String receiptId,
                                             PaymentObservation observation, String digest, String payload, String reason) {
        results.recordAnomaly(tenant, new PaymentResultAnomaly(ids.generate(), accepted.payment().id(),
                observation.identity().attemptId(), receiptId, reason, digest, payload, now()));
        return present(CONFLICT, receiptId, accepted);
    }

    private PaymentResultApplication receipt(String tenant, StoredPaymentAcceptance accepted, String id,
                                             PaymentObservation observation, String digest, String payload, PaymentReceiptDisposition disposition, String reason) {
        results.insertReceipt(tenant, converter.toReceipt(id, observation, digest, payload, disposition, reason, now()));
        return present(disposition, id, accepted);
    }

    private PaymentResultApplication present(PaymentReceiptDisposition disposition, String receiptId, StoredPaymentAcceptance accepted) {
        return new PaymentResultApplication(disposition, receiptId, views.toDTO(accepted));
    }

    private boolean terminal(PaymentState state) {
        return state == PaymentState.SUCCEEDED || state == PaymentState.FAILED || state == PaymentState.CLOSED;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
