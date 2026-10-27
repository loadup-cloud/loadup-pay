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
import io.github.loadup.pay.payment.app.converter.RefundExecutionConverter;
import io.github.loadup.pay.payment.app.converter.RefundPlanningConverter;
import io.github.loadup.pay.payment.app.model.LockedRefund;
import io.github.loadup.pay.payment.app.model.RefundResultApplication;
import io.github.loadup.pay.payment.app.port.RefundResultCodec;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition;
import io.github.loadup.pay.payment.domain.model.RefundResultAnomaly;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition.APPLIED;
import static io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition.CONFLICT;
import static io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition.IGNORED;

/**
 * Internal verified-adapter entry point only. There is no merchant success-injection endpoint.
 */
@Service
public class RefundResultService {
    private final RefundResultGateway results;
    private final RefundExecutionLocks locks;
    private final PaymentAcceptanceTransactions transactions;
    private final RefundResultCodec codec;
    private final RefundExecutionConverter converter;
    private final RefundPlanningConverter views;
    private final IdGenerator ids;
    private final Clock clock;
    private final RefundProjectionEvents projections;

    public RefundResultService(RefundResultGateway results, RefundExecutionLocks locks, PaymentAcceptanceTransactions transactions,
                               RefundResultCodec codec, RefundExecutionConverter converter, RefundPlanningConverter views, IdGenerator ids,
                               @Qualifier("paymentClock") Clock clock, RefundProjectionEvents projections) {
        this.results = results;
        this.locks = locks;
        this.transactions = transactions;
        this.codec = codec;
        this.converter = converter;
        this.views = views;
        this.ids = ids;
        this.clock = clock;
        this.projections = projections;
    }

    public RefundResultApplication record(String tenant, RefundObservation observation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (observation == null) throw new CommonException(INVALID_ARGUMENT);
        return transactions.withTenantContext(tenant, () -> {
            DomainChecks.id(tenant);
            String digest = codec.factsDigest(observation), payload = codec.encode(observation);
            String requestId = results.requestId(tenant, observation.refundId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            return transactions.writeLocal(tenant, () -> apply(tenant, locks.lock(tenant, requestId, observation.refundId()), observation, digest, payload));
        });
    }

    private RefundResultApplication apply(String tenant, LockedRefund locked, RefundObservation observation, String digest, String payload) {
        var request = locked.request();
        var facts = locked.child();
        var current = facts.child().refund();
        var known = results.receipt(tenant, current.id(), observation.source(), observation.receiptKey());
        if (known.isPresent()) {
            var receipt = known.get();
            if (!receipt.factsDigest().equals(digest))
                return anomaly(tenant, request, receipt.id(), observation, digest, payload, "RECEIPT_FACTS_CONFLICT");
            return present(receipt.disposition(), receipt.id(), request);
        }
        String id = ids.generate();
        String mismatch = mismatch(facts, observation);
        if (mismatch != null) return reject(tenant, request, id, observation, digest, payload, mismatch);
        if (observation.kind() == ChannelRefundOutcome.Kind.SUCCEEDED) {
            if (current.state() == RefundState.FAILED)
                return reject(tenant, request, id, observation, digest, payload, "TERMINAL_FACT_CONFLICT");
            var claimed = results.transaction(observation.original().channel(), observation.original().accountScopeHash(), observation.channelRefundTransactionNo());
            var own = results.claim(tenant, current.id());
            if (claimed.isPresent() && !matches(tenant, claimed.get(), observation))
                return reject(tenant, request, id, observation, digest, payload, "REFUND_TRANSACTION_ALREADY_CLAIMED");
            if (own.isPresent() && !matches(tenant, own.get(), observation))
                return reject(tenant, request, id, observation, digest, payload, "REFUND_TRANSACTION_CONFLICT");
            if (current.state() == RefundState.SUCCEEDED) {
                if (own.isEmpty() || claimed.isEmpty() || !own.get().id().equals(claimed.get().id())
                        || !Objects.equals(current.terminalEvidenceId(), own.get().receiptId())
                        || !observation.channelRefundTransactionNo().equals(facts.channelRefundTransactionNo())
                        || !observation.succeededAt().equals(facts.succeededAt()) || locked.capacity().refundedMinor() < current.amount().getCent())
                    throw new CommonException(SNAPSHOT_INVALID);
                return receipt(tenant, request, id, observation, digest, payload, IGNORED, null);
            }
            if (own.isPresent() || claimed.isPresent()) throw new CommonException(SNAPSHOT_INVALID);
            results.insertClaim(converter.toClaim(ids.generate(), tenant, observation, id));
        } else if (observation.kind() == ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND && current.state() == RefundState.SUCCEEDED)
            return reject(tenant, request, id, observation, digest, payload, "TERMINAL_FACT_CONFLICT");
        if (current.terminal()) return receipt(tenant, request, id, observation, digest, payload, IGNORED, null);
        var changed = locked.capacity().applyOutcome(current, converter.toOutcome(observation, id));
        if (changed.refund().equals(current))
            return receipt(tenant, request, id, observation, digest, payload, IGNORED, null);
        var next = request.request().apply(changed.refund());
        var change = new RefundProgressChange(facts, changed.refund(), request.request(), next, locked.capacity(), changed.capacity(),
                facts.submissionStartedAt(), observation.channelRefundTransactionNo(), observation.succeededAt());
        results.update(tenant, change);
        projections.changed(tenant, change);
        var updated = converter.withRefund(facts.child(), changed.refund());
        var stored = converter.withRequest(request, next, request.allocations().stream()
                .map(c -> c.refund().id().equals(current.id()) ? updated : c).toList());
        return receipt(tenant, stored, id, observation, digest, payload, APPLIED, null);
    }

    /**
     * A local ambiguous transport failure is not a verified channel result or a financial receipt.
     */
    public void uncertain(String tenant, String requestId, String refundId) {
        transactions.writeLocal(tenant, () -> {
            var locked = locks.lock(tenant, requestId, refundId);
            var current = locked.child().child().refund();
            if (current.terminal() || current.state() == RefundState.UNKNOWN) return true;
            if (locked.child().submissionStartedAt() == null) throw new CommonException(INVALID_STATE);
            var nextChild = current.recordOutcome(new ChannelRefundOutcome(refundId, ChannelRefundOutcome.Kind.UNKNOWN, current.amount(), null));
            var next = locked.request().request().apply(nextChild);
            var change = new RefundProgressChange(locked.child(), nextChild, locked.request().request(), next,
                    locked.capacity(), locked.capacity(), locked.child().submissionStartedAt(), null, null);
            results.update(tenant, change);
            projections.changed(tenant, change);
            return true;
        });
    }

    private String mismatch(RefundExecutionFacts facts, RefundObservation o) {
        var child = facts.child();
        if (!child.original().equals(o.original()) || !child.channelTransactionNo().equals(o.originalChannelTransactionNo())
                || !child.refund().channelRefundNo().equals(o.channelRefundNo())) return "REFUND_IDENTITY_MISMATCH";
        if (!child.refund().amount().equals(o.amount())) return "REFUND_AMOUNT_MISMATCH";
        if (facts.submissionStartedAt() == null) return "OBSERVATION_BEFORE_SUBMISSION";
        if (o.succeededAt() != null && o.succeededAt().isBefore(child.originalSucceededAt()))
            return "REFUND_SUCCESS_TIME_MISMATCH";
        return null;
    }

    private boolean matches(String tenant, RefundTransactionClaim claim, RefundObservation o) {
        return claim.tenantId().equals(tenant) && claim.refundId().equals(o.refundId()) && claim.original().equals(o.original())
                && claim.originalChannelTransactionNo().equals(o.originalChannelTransactionNo()) && claim.channelRefundNo().equals(o.channelRefundNo())
                && claim.amount().equals(o.amount()) && claim.channelRefundTransactionNo().equals(o.channelRefundTransactionNo())
                && claim.succeededAt().equals(o.succeededAt());
    }

    private RefundResultApplication reject(String tenant, StoredRefundRequest request, String id, RefundObservation o, String digest, String payload, String reason) {
        receipt(tenant, request, id, o, digest, payload, CONFLICT, reason);
        return anomaly(tenant, request, id, o, digest, payload, reason);
    }

    private RefundResultApplication anomaly(String tenant, StoredRefundRequest request, String id, RefundObservation o, String digest, String payload, String reason) {
        results.recordAnomaly(tenant, new RefundResultAnomaly(ids.generate(), o.refundId(), id, reason, digest, payload, now()));
        return present(CONFLICT, id, request);
    }

    private RefundResultApplication receipt(String tenant, StoredRefundRequest request, String id, RefundObservation o, String digest, String payload,
                                            RefundReceiptDisposition disposition, String reason) {
        results.insertReceipt(tenant, converter.toReceipt(id, o, digest, payload, disposition, reason, now()));
        return present(disposition, id, request);
    }

    private RefundResultApplication present(RefundReceiptDisposition disposition, String id, StoredRefundRequest request) {
        return new RefundResultApplication(disposition, id, views.toDTO(request.request(), views.toAllocations(request.allocations())));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
