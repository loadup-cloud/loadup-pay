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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.app.converter.PaymentRealtimeConverter;
import io.github.loadup.pay.payment.app.converter.PaymentResultConverter;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Each target has its own durable delivery; handlers must revalidate authority before financial side effects.
 */
@Component
public class PaymentResultEvents {
    public static final String ACQUIRE = "pay.payment.outcome.acquire.v1";
    public static final String LIMIT = "pay.payment.outcome.limit.v1";
    public static final String REALTIME = "pay.payment.snapshot.realtime.v1";
    public static final String RISK = "pay.payment.outcome.risk.v1";
    private final OutboxTemplate outbox;
    private final PaymentResultConverter converter;
    private final IdGenerator ids;
    private final PaymentAttemptGateway attempts;
    private final PaymentResultGateway results;
    private final PaymentRealtimeConverter snapshots;

    public PaymentResultEvents(OutboxTemplate outbox, PaymentResultConverter converter, IdGenerator ids,
                               PaymentAttemptGateway attempts, PaymentResultGateway results, PaymentRealtimeConverter snapshots) {
        this.outbox = outbox;
        this.converter = converter;
        this.ids = ids;
        this.attempts = attempts;
        this.results = results;
        this.snapshots = snapshots;
    }

    public void publish(String tenant, StoredPaymentAcceptance payment, boolean fenced, LocalDateTime occurredAt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new CommonException(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE);
        var data = converter.toEvent(payment, fenced);
        for (var type : List.of(ACQUIRE, LIMIT, RISK)) {
            var envelope = new EventEnvelope<>(ids.generate(), payment.terminalEvidenceId(), "payment", type, 1,
                    payment.payment().id(), payment.payment().version(), BusinessTime.external(occurredAt), data);
            outbox.publish(new OutboxMessage(tenant, type, 1, payment.payment().id(), JsonUtil.toJson(envelope)));
        }
        StoredPaymentAttempt attempt = payment.payment().activeAttemptId() == null ? null
                : attempts.find(tenant, payment.payment().activeAttemptId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (attempt != null && (!attempt.attempt().paymentId().equals(payment.payment().id())
                || !attempt.attempt().amount().equals(payment.payment().amount())
                || !attempt.attempt().binding().payeeMerchantId().equals(payment.payment().payeeMerchantId())))
            throw new CommonException(SNAPSHOT_INVALID);
        LocalDateTime succeededAt = null;
        if (payment.payment().state() == PaymentState.SUCCEEDED) {
            if (attempt == null || attempt.attempt().state() != PaymentAttemptState.SUCCEEDED)
                throw new CommonException(SNAPSHOT_INVALID);
            var claim = results.attemptClaim(tenant, attempt.attempt().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!claim.tenantId().equals(tenant) || !claim.identity().equals(attempt.attempt().identity())
                    || !claim.amount().equals(payment.payment().amount()) || !claim.receiptId().equals(payment.terminalEvidenceId()))
                throw new CommonException(SNAPSHOT_INVALID);
            succeededAt = claim.succeededAt();
        }
        var snapshot = snapshots.toSnapshot(payment, attempt == null ? null : attempt.attempt().binding().channel(),
                attempt == null ? null : attempt.attempt().binding().accessMode(), succeededAt);
        var envelope = new EventEnvelope<>(ids.generate(), payment.terminalEvidenceId(), "payment", REALTIME, 1,
                payment.payment().id(), payment.payment().version(), BusinessTime.external(occurredAt), snapshot);
        outbox.publish(new OutboxMessage(tenant, REALTIME, 1, payment.payment().id(), JsonUtil.toJson(envelope)));
    }
}
