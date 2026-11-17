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

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.FrozenEventTypes;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.app.converter.PaymentResultConverter;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.payment.app.port.PaymentOutcomeEnvelopeCodec;
import io.github.loadup.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.payment.domain.model.GateState;
import io.github.loadup.payment.domain.model.PaymentAttemptState;
import io.github.loadup.payment.domain.model.StoredPaymentAttempt;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Only the local Outbox dispatcher calls this seam. Queue labels are not user/service authentication.
 */
@Component
public class PaymentOutcomeDeliverySupport {
    private final TaskTemplate outbox;
    private final PaymentOutcomeEnvelopeCodec envelopes;
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentResultGateway results;
    private final PaymentExecutionLocks locks;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentAcceptanceCodec intents;
    private final PaymentResultConverter converter;

    public PaymentOutcomeDeliverySupport(
            TaskTemplate outbox,
            PaymentOutcomeEnvelopeCodec envelopes,
            PaymentAcceptanceGateway payments,
            PaymentAttemptGateway attempts,
            PaymentResultGateway results,
            PaymentExecutionLocks locks,
            PaymentAcceptanceTransactions transactions,
            PaymentAcceptanceCodec intents,
            PaymentResultConverter converter) {
        this.outbox = outbox;
        this.envelopes = envelopes;
        this.payments = payments;
        this.attempts = attempts;
        this.results = results;
        this.locks = locks;
        this.transactions = transactions;
        this.intents = intents;
        this.converter = converter;
    }

    public PaymentDeliverySnapshot load(TaskExecution event, String expectedType) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null || event.payloadVersion() != 1 || !expectedType.equals(event.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        DomainChecks.id(event.id());
        var queue = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!queue.id().equals(event.id())
                || !queue.taskType().equals(expectedType)
                || !queue.bizId().equals(event.bizId())
                || queue.status() != TaskStatus.RUNNING) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (!Set.of(PaymentDeliveryRoutes.ACQUIRE, PaymentDeliveryRoutes.LIMIT, PaymentDeliveryRoutes.RISK)
                .contains(expectedType)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var envelope = envelopes.decode(event.payload());
        if (envelope == null
                || envelope.data() == null
                || envelope.schemaVersion() != 1
                || !"payment".equals(envelope.producer())
                || !(FrozenEventTypes.accepts(BusinessEventTypes.PAYMENT_FINANCIAL, expectedType, envelope.eventType())
                        || BusinessEventTypes.PAYMENT_OUTCOME.equals(envelope.eventType()))
                || !queue.bizId().equals(envelope.aggregateId())
                || !envelope.aggregateId().equals(envelope.data().paymentId())
                || envelope.aggregateVersion() != envelope.data().paymentVersion()
                || !envelope.businessEventId().equals(envelope.data().evidenceId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var data = envelope.data();
        if ((data.financialEvidence() != null) != BusinessEventTypes.PAYMENT_FINANCIAL.equals(envelope.eventType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (!Set.of(PaymentState.SUCCEEDED, PaymentState.FAILED, PaymentState.CLOSED)
                .contains(data.state())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return transactions.guarded(() -> {
            var original = payments.find(data.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            return transactions.writeLocal(() -> {
                var locked = locks.lock(data.paymentId(), original.callerServiceId());
                var current = locked.payment();
                var accepted = intents.decode(current);
                boolean fenced = locked.gate().gate().state() == GateState.FENCED;
                boolean succeeded = current.payment().state() == PaymentState.SUCCEEDED;
                // A later fence cannot invalidate an earlier positive terminal fact.
                if (!converter.toEvent(current, data.executionFenced()).equals(data.withoutFinancialEvidence())
                        || data.executionFenced() && !fenced
                        || !succeeded && !fenced) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                StoredPaymentAttempt attempt = null;
                if (current.payment().activeAttemptId() != null) {
                    attempt = attempts.lock(current.payment().activeAttemptId())
                            .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    PaymentExecutionBindings.route(
                            PaymentExecutionBindings.activeContext(locked, attempt)
                                    .attempt(),
                            accepted.choice().channel(),
                            accepted.choice().method(),
                            accepted.preparedInputRef());
                }
                LocalDateTime succeededAt = null;
                if (succeeded) {
                    if (attempt == null || attempt.attempt().state() != PaymentAttemptState.SUCCEEDED) {
                        throw new CommonException(SNAPSHOT_INVALID);
                    }
                    var claim = results.attemptClaim(attempt.attempt().id())
                            .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    var capacity = results.capacity(current.payment().id())
                            .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    if (!claim.identity().equals(attempt.attempt().identity())
                            || !claim.amount().equals(current.payment().amount())
                            || !claim.receiptId().equals(current.terminalEvidenceId())
                            || !capacity.succeededAmount()
                                    .equals(current.payment().amount())
                            || !capacity.orderId().equals(current.payment().orderId())
                            || !capacity.payeeMerchantId()
                                    .equals(current.payment().payeeMerchantId())
                            || !capacity.succeededAt().equals(claim.succeededAt())) {
                        throw new CommonException(SNAPSHOT_INVALID);
                    }
                    succeededAt = claim.succeededAt();
                } else if (attempt != null
                        && (!attempt.attempt().canSafelyReplace()
                                || !Objects.equals(
                                        attempt.attempt().noCollectionEvidenceId(), current.terminalEvidenceId()))) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                if (data.financialEvidence() != null) {
                    var verified = converter.toFinancialEvidence(
                            current,
                            succeededAt == null ? null : BusinessTime.external(succeededAt),
                            data.executionFenced());
                    if (!verified.equals(data.financialEvidence())) {
                        throw new CommonException(SNAPSHOT_INVALID);
                    }
                }
                return new PaymentDeliverySnapshot(envelope, current, attempt, succeededAt);
            });
        });
    }
}
