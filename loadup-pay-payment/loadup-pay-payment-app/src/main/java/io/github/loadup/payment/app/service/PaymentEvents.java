/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.BusinessFact;
import io.github.loadup.contracts.event.payment.PaymentSnapshotEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.app.converter.PaymentRealtimeConverter;
import io.github.loadup.payment.app.converter.PaymentResultConverter;
import io.github.loadup.payment.app.dispatch.PaymentFactPublisher;
import io.github.loadup.payment.app.dispatch.PaymentFactRoutes;
import io.github.loadup.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.payment.domain.model.PaymentAttemptState;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.model.StoredPaymentAttempt;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Complete lifecycle snapshots and terminal-only financial deliveries share the original local
 * transaction.
 */
@Component
public class PaymentEvents {
    private final PaymentFactPublisher facts;
    private final PaymentResultConverter converter;
    private final IdGenerator ids;
    private final PaymentAttemptGateway attempts;
    private final PaymentResultGateway results;
    private final PaymentRealtimeConverter snapshots;

    public PaymentEvents(
            TaskTemplate outbox,
            PaymentResultConverter converter,
            IdGenerator ids,
            PaymentAttemptGateway attempts,
            PaymentResultGateway results,
            PaymentRealtimeConverter snapshots,
            PaymentFactRoutes routes) {
        this.facts = new PaymentFactPublisher(outbox, ids, routes);
        this.converter = converter;
        this.ids = ids;
        this.attempts = attempts;
        this.results = results;
        this.snapshots = snapshots;
    }

    public void publish(StoredPaymentAcceptance payment, boolean fenced, LocalDateTime occurredAt) {
        if (payment == null
                || occurredAt == null
                || !PaymentExecutionService.terminal(payment.payment().state())
                || payment.terminalEvidenceId() == null) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var snapshot = freezeSnapshot(payment, occurredAt);
        var evidence = converter.toFinancialEvidence(payment, snapshot.data().succeededAt(), fenced);
        var data = converter.toEvent(payment, fenced).withFinancialEvidence(evidence);
        facts.publish(new BusinessFact<>(
                payment.terminalEvidenceId(),
                "payment",
                BusinessEventTypes.PAYMENT_FINANCIAL,
                1,
                payment.payment().id(),
                payment.payment().version(),
                BusinessTime.external(occurredAt),
                data));
        facts.publish(snapshot);
    }

    /**
     * Caller invokes this only after inserting or changing the payment aggregate version. No
     * financial delivery is emitted.
     */
    public void publishSnapshot(StoredPaymentAcceptance payment, LocalDateTime occurredAt) {
        facts.publish(freezeSnapshot(payment, occurredAt));
    }

    private BusinessFact<PaymentSnapshotEventDTO> freezeSnapshot(
            StoredPaymentAcceptance payment, LocalDateTime occurredAt) {
        if (payment == null || occurredAt == null) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        boolean terminal = PaymentExecutionService.terminal(payment.payment().state());
        if (terminal != (payment.terminalEvidenceId() != null)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        StoredPaymentAttempt attempt = payment.payment().activeAttemptId() == null
                ? null
                : attempts.find(payment.payment().activeAttemptId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (attempt != null) {
            PaymentExecutionBindings.active(payment, attempt);
            if (payment.payment().state() == PaymentState.UNKNOWN) {
                PaymentExecutionBindings.observable(payment, attempt);
            }
            if (payment.payment().state() == PaymentState.INIT) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            if ((payment.payment().state() == PaymentState.FAILED
                            || payment.payment().state() == PaymentState.CLOSED)
                    && (!attempt.attempt().canSafelyReplace()
                            || !payment.terminalEvidenceId()
                                    .equals(attempt.attempt().noCollectionEvidenceId()))) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        } else if (payment.payment().state() == PaymentState.PROCESSING
                || payment.payment().state() == PaymentState.UNKNOWN
                || payment.payment().state() == PaymentState.SUCCEEDED) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        LocalDateTime succeededAt = null;
        if (payment.payment().state() == PaymentState.SUCCEEDED) {
            if (attempt == null || attempt.attempt().state() != PaymentAttemptState.SUCCEEDED) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var claim = results.attemptClaim(attempt.attempt().id())
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!claim.identity().equals(attempt.attempt().identity())
                    || !claim.amount().equals(payment.payment().amount())
                    || !claim.receiptId().equals(payment.terminalEvidenceId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            succeededAt = claim.succeededAt();
        }
        final PaymentSnapshotEventDTO snapshot;
        try {
            snapshot = snapshots.toSnapshot(
                    payment,
                    attempt == null ? null : attempt.attempt().binding().channel(),
                    attempt == null ? null : attempt.attempt().binding().accessMode(),
                    succeededAt);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return new BusinessFact<>(
                terminal ? payment.terminalEvidenceId() : ids.generate(),
                "payment",
                BusinessEventTypes.PAYMENT_SNAPSHOT,
                1,
                payment.payment().id(),
                payment.payment().version(),
                BusinessTime.external(occurredAt),
                snapshot);
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(PaymentResultCodeEnum.INVALID_STATE);
        }
    }
}
