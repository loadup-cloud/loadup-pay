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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentResultConverter;
import io.github.loadup.pay.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentOutcomeEnvelopeCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Set;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Only the local Outbox dispatcher calls this seam. Queue labels are not user/service authentication.
 */
@Component
public class PaymentOutcomeDeliverySupport {
    private final OutboxTemplate outbox;
    private final PaymentOutcomeEnvelopeCodec envelopes;
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentResultGateway results;
    private final PaymentExecutionLocks locks;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentAcceptanceCodec intents;
    private final PaymentResultConverter converter;

    public PaymentOutcomeDeliverySupport(OutboxTemplate outbox, PaymentOutcomeEnvelopeCodec envelopes,
                                         PaymentAcceptanceGateway payments, PaymentAttemptGateway attempts, PaymentResultGateway results,
                                         PaymentExecutionLocks locks, PaymentAcceptanceTransactions transactions, PaymentAcceptanceCodec intents,
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

    public PaymentDeliverySnapshot load(OutboxEvent event, String expectedType) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (event == null || event.message() == null || event.message().version() != 1
                || !expectedType.equals(event.message().type())
                || !event.message().tenantId().equals(TenantUtil.getTenantId()))
            throw new CommonException(SNAPSHOT_INVALID);
        String tenant = event.message().tenantId();
        DomainChecks.id(event.id());
        var queue = outbox.find(tenant, event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!queue.id().equals(event.id()) || !queue.tenantId().equals(tenant) || !queue.type().equals(expectedType)
                || !queue.businessId().equals(event.message().businessId()) || !"PROCESSING".equals(queue.status()))
            throw new CommonException(SNAPSHOT_INVALID);
        var envelope = envelopes.decode(event.message().payload());
        if (envelope == null || envelope.data() == null || envelope.schemaVersion() != 1 || !"payment".equals(envelope.producer())
                || !expectedType.equals(envelope.eventType()) || !queue.businessId().equals(envelope.aggregateId())
                || !envelope.aggregateId().equals(envelope.data().paymentId())
                || envelope.aggregateVersion() != envelope.data().paymentVersion()
                || !envelope.businessEventId().equals(envelope.data().evidenceId()))
            throw new CommonException(SNAPSHOT_INVALID);
        var data = envelope.data();
        if (!Set.of(io.github.loadup.pay.payment.client.enums.PaymentState.SUCCEEDED,
                io.github.loadup.pay.payment.client.enums.PaymentState.FAILED,
                io.github.loadup.pay.payment.client.enums.PaymentState.CLOSED).contains(data.state()))
            throw new CommonException(SNAPSHOT_INVALID);
        return transactions.read(tenant, () -> {
            var original = payments.find(tenant, data.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            return transactions.write(tenant, () -> {
                var locked = locks.lock(tenant, data.paymentId(), original.callerServiceId());
                var current = locked.payment();
                var accepted = intents.decode(current);
                boolean fenced = locked.gate().gate().state() == GateState.FENCED;
                boolean succeeded = current.payment().state() == PaymentState.SUCCEEDED;
                // A later fence cannot invalidate an earlier positive terminal fact.
                if (!converter.toEvent(current, data.executionFenced()).equals(data)
                        || data.executionFenced() && !fenced || !succeeded && !fenced)
                    throw new CommonException(SNAPSHOT_INVALID);
                StoredPaymentAttempt attempt = null;
                if (current.payment().activeAttemptId() != null) {
                    attempt = attempts.lock(tenant, current.payment().activeAttemptId())
                            .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    var value = attempt.attempt();
                    if (!value.paymentId().equals(current.payment().id()) || !value.amount().equals(current.payment().amount())
                            || !value.binding().payeeMerchantId().equals(current.payment().payeeMerchantId())
                            || !attempt.paymentRequestDigest().equals(current.requestDigest())
                            || !attempt.callerServiceId().equals(current.callerServiceId())
                            || !attempt.limitOperationId().equals(current.payment().limitOperationId())
                            || value.binding().channel() != accepted.choice().channel() || value.method() != accepted.choice().method()
                            || !Objects.equals(attempt.preparedInputRef(), accepted.preparedInputRef()))
                        throw new CommonException(SNAPSHOT_INVALID);
                }
                java.time.LocalDateTime succeededAt = null;
                if (succeeded) {
                    if (attempt == null || attempt.attempt().state() != PaymentAttemptState.SUCCEEDED)
                        throw new CommonException(SNAPSHOT_INVALID);
                    var claim = results.attemptClaim(tenant, attempt.attempt().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    var capacity = results.capacity(tenant, current.payment().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    if (!claim.tenantId().equals(tenant) || !claim.identity().equals(attempt.attempt().identity())
                            || !claim.amount().equals(current.payment().amount()) || !claim.receiptId().equals(current.terminalEvidenceId())
                            || !capacity.succeededAmount().equals(current.payment().amount())
                            || !capacity.orderId().equals(current.payment().orderId())
                            || !capacity.payeeMerchantId().equals(current.payment().payeeMerchantId())
                            || !capacity.succeededAt().equals(claim.succeededAt()))
                        throw new CommonException(SNAPSHOT_INVALID);
                    succeededAt = claim.succeededAt();
                } else if (attempt != null && (!attempt.attempt().canSafelyReplace()
                        || !Objects.equals(attempt.attempt().noCollectionEvidenceId(), current.terminalEvidenceId()))) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                return new PaymentDeliverySnapshot(tenant, envelope, current, attempt, succeededAt);
            });
        });
    }
}
