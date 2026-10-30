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
package io.github.loadup.pay.reconciliation.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationVerificationConverter;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationSubmitCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationVerificationDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationVerificationFacade;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationVerificationGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationState;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Set;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Frozen intent and original Outbox publication precede every owner query. No funds posting or source result input.
 */
@Service
public class ReconciliationVerificationService implements ReconciliationVerificationFacade {
    private final ReconciliationActorProvider actors;
    private final ReconciliationReviewService reviews;
    private final ReconciliationVerificationGateway requests;
    private final ReconciliationTransactions transactions;
    private final OutboxTemplate outbox;
    private final ReconciliationVerificationConverter converter;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;

    public ReconciliationVerificationService(ReconciliationActorProvider actors, ReconciliationReviewService reviews, ReconciliationVerificationGateway requests,
                                             ReconciliationTransactions transactions, OutboxTemplate outbox, ReconciliationVerificationConverter converter, IdGenerator ids, Validator validator,
                                             Clock clock) {
        this.actors = actors;
        this.reviews = reviews;
        this.requests = requests;
        this.transactions = transactions;
        this.outbox = outbox;
        this.converter = converter;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
    }

    static OutboxRecord delivery(OutboxTemplate outbox, ReconciliationVerificationRequest value) {
        var row = outbox.find(value.deliveryId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!value.deliveryId().equals(row.id()) || !value.id().equals(row.businessId())
                || !ReconciliationVerificationMessages.TYPE.equals(row.type()) || row.status() == null || !Set.of("PENDING", "PROCESSING", "SUCCEEDED", "FAILED").contains(row.status())
                || row.attempts() < 0 || "SUCCEEDED".equals(row.status()) && value.state() == ReconciliationVerificationState.INIT) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return row;
    }

    @Override
    public ReconciliationVerificationDTO submit(ReconciliationVerificationSubmitCommand command) {
        validate(command);
        var actor = actor("submit");
        var digest = digest(actor, command);
        return transactions.writeLocal(actor.tenantId(), () -> {
            var approved = reviews.approvedForVerification(actor, command.batchId(), command.differenceId(), command.proposalId(), command.approvalEventId(), command.expectedReviewVersion(), true);
            var old = requests.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (old.isPresent()) {
                var value = checked(actor, old.get());
                if (!value.requestDigest().equals(digest)) {
                    throw new CommonException(IDEMPOTENCY_CONFLICT);
                }
                return present(actor.tenantId(), value);
            }
            var id = ids.generate();
            var deliveryId = outbox.publish(new OutboxMessage(ReconciliationVerificationMessages.TYPE, 1, id, ReconciliationVerificationMessages.encode(id)));
            var request = converter.toRequest(approved, id, actor.serviceId(), command.requestKey(), digest, deliveryId);
            requests.insert(actor.tenantId(), request);
            return present(actor.tenantId(), request);
        });
    }

    @Override
    public ReconciliationVerificationDTO detail(IdQuery query) {
        validate(query);
        var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> present(actor.tenantId(), checked(actor, requests.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND)))));
    }

    @Override
    public ReconciliationVerificationDTO retry(ReconciliationVerificationRetryCommand command) {
        validate(command);
        var actor = actor("retry");
        var original = transactions.readSnapshot(actor.tenantId(), () -> checked(actor, requests.find(actor.tenantId(), command.requestId()).orElseThrow(() -> new CommonException(NOT_FOUND))));
        return transactions.writeLocal(actor.tenantId(), () -> {
            // Stable batch -> review -> verification order. Retry never replaces the frozen query or recorded observation.
            reviews.approvedForVerification(actor, original.reference().batchId(), original.reference().differenceId(), original.proposalId(), original.approvalEventId(), original.reviewVersion(), true);
            var value = checked(actor, requests.lock(actor.tenantId(), original.id()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            if (!value.deliveryId().equals(command.expectedDeliveryId())) {
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            }
            var delivery = delivery(outbox, value);
            if ("FAILED".equals(delivery.status())) {
                outbox.replay(delivery.id(), actor.serviceId(), command.reason());
            }
            return present(actor.tenantId(), value);
        });
    }

    private ReconciliationVerificationRequest checked(ReconciliationActor actor, ReconciliationVerificationRequest value) {
        if (!actor.serviceId().equals(value.callerServiceId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var approved = reviews.approvedForVerification(actor, value.reference().batchId(), value.reference().differenceId(), value.proposalId(), value.approvalEventId(), value.reviewVersion(), false);
        if (!value.reference().equals(approved.reference()) || !value.scope().equals(approved.scope()) || !value.key().equals(approved.key()) || !value.merchantPaymentNo().equals(approved.merchantPaymentNo())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return value;
    }

    private ReconciliationVerificationDTO present(String tenant, ReconciliationVerificationRequest value) {
        return converter.toDTO(value, delivery(outbox, value), clock);
    }

    private ReconciliationActor actor(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var actor = actors.current();
        if (actor == null || !actor.permissions().contains("pay:reconciliation:verification:" + operation)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private String digest(ReconciliationActor actor, ReconciliationVerificationSubmitCommand command) {
        var value = new StringBuilder();
        for (var field : List.of("reconciliation-verification-v1", actor.tenantId(), actor.serviceId(), command.batchId(), command.differenceId(), command.proposalId(), command.approvalEventId(), Long.toString(command.expectedReviewVersion()), command.requestKey())) {
            value.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return DigestUtils.sha256(value.toString());
    }
}
