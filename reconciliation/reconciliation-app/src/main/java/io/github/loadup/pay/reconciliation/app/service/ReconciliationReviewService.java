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
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationReviewConverter;
import io.github.loadup.pay.reconciliation.app.model.StoredReconciliationDifference;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationDifferenceReader;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewDecideCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewEventDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationSourceFileDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationReviewFacade;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewHistoryQuery;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewQuery;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationReviewGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewConclusion;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewOperation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.REVIEW_CHECKER_REQUIRED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.REVIEW_VERSION_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Independent administrative conclusions. No financial gateway, source RPC or batch mutation is exposed.
 */
@Service
public class ReconciliationReviewService implements ReconciliationReviewFacade {
    private final ReconciliationActorProvider actors;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationDifferenceReader differences;
    private final ReconciliationReviewGateway reviews;
    private final ReconciliationSourceFiles files;
    private final ReconciliationTransactions transactions;
    private final ReconciliationReviewConverter converter;
    private final ReconciliationConverter differenceConverter;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;

    public ReconciliationReviewService(ReconciliationActorProvider actors, ReconciliationAccountDirectory accounts,
                                       ReconciliationBatchGateway batches, ReconciliationDifferenceReader differences, ReconciliationReviewGateway reviews,
                                       ReconciliationSourceFiles files, ReconciliationTransactions transactions, ReconciliationReviewConverter converter,
                                       ReconciliationConverter differenceConverter, IdGenerator ids, Validator validator, Clock clock) {
        this.actors = actors;
        this.accounts = accounts;
        this.batches = batches;
        this.differences = differences;
        this.reviews = reviews;
        this.files = files;
        this.transactions = transactions;
        this.converter = converter;
        this.differenceConverter = differenceConverter;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
    }

    @Override
    public ReconciliationSourceFileDTO uploadEvidence(String filename, String type, long size, InputStream content) {
        var actor = actor("upload");
        return converter.toDTO(files.upload(actor.tenantId(), actor.serviceId(), filename, type, size, content));
    }

    @Override
    public ReconciliationReviewEventDTO propose(ReconciliationReviewProposeCommand command) {
        validate(command);
        var actor = actor("propose");
        note(command.note());
        String digest = digest(actor, "PROPOSE", command.batchId(), command.differenceId(), command.requestKey(), Long.toString(command.expectedVersion()), command.conclusion().name(), command.note(), command.evidenceFileId());
        var replay = transactions.readSnapshot(actor.tenantId(), () -> reviews.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey())
                .map(event -> replay(actor, event, digest, command.batchId(), command.differenceId())));
        if (replay.isPresent()) {
            return replay.get();
        }
        transactions.readSnapshot(actor.tenantId(), () -> evidence(actor, command.batchId(), command.differenceId(), false));
        var file = command.evidenceFileId() == null ? null : files.inspect(actor.tenantId(), actor.serviceId(), command.evidenceFileId());
        return transactions.writeLocal(actor.tenantId(), () -> {
            var source = evidence(actor, command.batchId(), command.differenceId(), true);
            var old = reviews.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (old.isPresent()) {
                return replay(actor, old.get(), digest, command.batchId(), command.differenceId());
            }
            var previous = head(actor.tenantId(), source.reference(), true);
            if (previous.version() != command.expectedVersion()) {
                throw new CommonException(REVIEW_VERSION_CONFLICT);
            }
            var at = now();
            var proposal = converter.toProposal(source.reference(), ids.generate(), actor.serviceId(), command, file, at);
            var next = previous.propose(proposal, ids.generate(), command.expectedVersion());
            var event = converter.toEvent(next, next.lastEventId(), actor.serviceId(), command.requestKey(), digest, ReconciliationReviewOperation.PROPOSE, null, at);
            if (file != null) {
                files.attachReview(actor.tenantId(), actor.serviceId(), file, proposal.id());
            }
            reviews.commit(actor.tenantId(), previous, next, proposal, event);
            return converter.toDTO(event, clock);
        });
    }

    @Override
    public ReconciliationReviewEventDTO decide(ReconciliationReviewDecideCommand command) {
        validate(command);
        var actor = actor("decide");
        note(command.note());
        String digest = digest(actor, command.approve() ? "APPROVE" : "REJECT", command.batchId(), command.differenceId(), command.requestKey(), Long.toString(command.expectedVersion()), command.proposalId(), command.note());
        return transactions.writeLocal(actor.tenantId(), () -> {
            var source = evidence(actor, command.batchId(), command.differenceId(), true);
            var old = reviews.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (old.isPresent()) {
                return replay(actor, old.get(), digest, command.batchId(), command.differenceId());
            }
            var previous = head(actor.tenantId(), source.reference(), true);
            if (previous.version() != command.expectedVersion() || !command.proposalId().equals(previous.proposalId())) {
                throw new CommonException(REVIEW_VERSION_CONFLICT);
            }
            if (previous.state() != ReconciliationReviewState.PROPOSED) {
                throw new CommonException(INVALID_STATE);
            }
            var proposal = proposal(actor.tenantId(), source.reference(), previous.proposalId());
            if (actor.serviceId().equals(proposal.proposerServiceId())) {
                throw new CommonException(REVIEW_CHECKER_REQUIRED);
            }
            var next = previous.decide(proposal, actor.serviceId(), ids.generate(), command.approve(), command.expectedVersion());
            var event = converter.toEvent(next, next.lastEventId(), actor.serviceId(), command.requestKey(), digest,
                    command.approve() ? ReconciliationReviewOperation.APPROVE : ReconciliationReviewOperation.REJECT, command.note(), now());
            reviews.commit(actor.tenantId(), previous, next, null, event);
            return converter.toDTO(event, clock);
        });
    }

    @Override
    public ReconciliationReviewDTO detail(ReconciliationReviewQuery query) {
        validate(query);
        var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> {
            var source = evidence(actor, query.batchId(), query.differenceId(), false);
            var head = head(actor.tenantId(), source.reference(), false);
            var proposal = head.proposalId() == null ? null : proposal(actor.tenantId(), source.reference(), head.proposalId());
            var event = head.lastEventId() == null ? null : event(actor.tenantId(), source.reference(), head.lastEventId());
            return converter.toDTO(head, proposal == null ? null : converter.toDTO(proposal, clock), event == null ? null : converter.toDTO(event, clock), differenceConverter.toDTO(source.difference()));
        });
    }

    @Override
    public PageDTO<ReconciliationReviewEventDTO> history(ReconciliationReviewHistoryQuery query) {
        validate(query);
        var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> {
            var source = evidence(actor, query.batchId(), query.differenceId(), false);
            var head = head(actor.tenantId(), source.reference(), false);
            var page = reviews.history(actor.tenantId(), query.differenceId(), query.page(), query.size());
            long offset = (long) (query.page() - 1) * query.size();
            if (page.page() != query.page() || page.size() != query.size() || page.total() != head.version()
                    || page.records().size() != Math.min(query.size(), Math.max(0L, head.version() - offset))) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            for (var event : page.records()) {
                if (event.version() != ++offset) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                verifyEvent(actor.tenantId(), source.reference(), event);
            }
            return PageDTO.of(page.map(value -> converter.toDTO(value, clock)));
        });
    }

    /**
     * Internal approved-scope read for the verification use case; caller already owns its local transaction and permission.
     */
    public io.github.loadup.pay.reconciliation.app.model.ApprovedReconciliationReview approvedForVerification(ReconciliationActor actor, String batchId, String differenceId,
                                                                                                              String proposalId, String approvalEventId, long expectedVersion, boolean lock) {
        var source = evidence(actor, batchId, differenceId, lock);
        var head = head(actor.tenantId(), source.reference(), lock);
        if (head.state() != ReconciliationReviewState.APPROVED || head.version() != expectedVersion || !proposalId.equals(head.proposalId()) || !approvalEventId.equals(head.lastEventId())) {
            throw new CommonException(REVIEW_VERSION_CONFLICT);
        }
        var proposal = proposal(actor.tenantId(), source.reference(), proposalId);
        if (proposal.conclusion() != ReconciliationReviewConclusion.NEEDS_SOURCE_VERIFICATION) {
            throw new CommonException(INVALID_STATE);
        }
        var line = source.difference().difference().ledger() != null ? source.difference().difference().ledger() : source.difference().difference().channel();
        return new io.github.loadup.pay.reconciliation.app.model.ApprovedReconciliationReview(source.reference(), source.scope(), source.difference().difference().key(), line.merchantPaymentNo(), proposalId, approvalEventId, expectedVersion);
    }

    private ReconciliationReviewEventDTO replay(ReconciliationActor actor, ReconciliationReviewEvent event, String digest, String batchId, String differenceId) {
        var source = evidence(actor, batchId, differenceId, false);
        if (!event.callerServiceId().equals(actor.serviceId()) || !event.requestDigest().equals(digest)) {
            throw new CommonException(IDEMPOTENCY_CONFLICT);
        }
        verifyEvent(actor.tenantId(), source.reference(), event);
        var head = head(actor.tenantId(), source.reference(), false);
        if (event.version() > head.version()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return converter.toDTO(event, clock);
    }

    private ReconciliationReview head(String tenant, ReconciliationReviewReference reference, boolean lock) {
        var value = (lock ? reviews.lock(tenant, reference.differenceId()) : reviews.find(tenant, reference.differenceId())).orElseGet(() -> ReconciliationReview.init(reference));
        if (!reference.equals(value.reference())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (value.lastEventId() != null) {
            var event = event(tenant, reference, value.lastEventId());
            try {
                event.verifyHead(value);
            } catch (io.github.loadup.commons.error.DomainViolation corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }
        return value;
    }

    private ReconciliationReviewProposal proposal(String tenant, ReconciliationReviewReference reference, String id) {
        var value = reviews.proposal(tenant, id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!id.equals(value.id()) || !reference.equals(value.reference())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (value.evidence() != null) {
            files.verifyReview(tenant, value.proposerServiceId(), value.evidence(), value.id());
        }
        return value;
    }

    private ReconciliationReviewEvent event(String tenant, ReconciliationReviewReference reference, String id) {
        var value = reviews.event(tenant, id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!id.equals(value.id())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        verifyEvent(tenant, reference, value);
        return value;
    }

    private void verifyEvent(String tenant, ReconciliationReviewReference reference, ReconciliationReviewEvent event) {
        if (!reference.equals(event.reference())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var proposal = proposal(tenant, reference, event.proposalId());
        boolean same = proposal.proposerServiceId().equals(event.callerServiceId());
        if ((event.operation() == ReconciliationReviewOperation.PROPOSE) != same) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private Evidence evidence(ReconciliationActor actor, String batchId, String differenceId, boolean lock) {
        var stored = (lock ? batches.lock(actor.tenantId(), batchId) : batches.find(actor.tenantId(), batchId)).orElseThrow(() -> new CommonException(NOT_FOUND));
        // Review permissions authorize independent account reviewers; original import/batch mutation permissions remain separate.
        accounts.authorize(actor.tenantId(), actor.serviceId(), stored.channelAccountCode(), stored.batch().scope());
        if (stored.batch().state() != ReconciliationState.DIFFERENCES || stored.batch().result() == null) {
            throw new CommonException(INVALID_STATE);
        }
        var difference = differences.find(actor.tenantId(), batchId, differenceId).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!differenceId.equals(difference.id()) || !batchId.equals(difference.batchId()) || !stored.batch().runId().equals(difference.runId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return new Evidence(converter.toReference(difference, stored.batch().result().resultHash()), difference, stored.batch().scope());
    }

    private ReconciliationActor actor(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var actor = actors.current();
        if (actor == null || !actor.permissions().contains("pay:reconciliation:review:" + operation)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private void note(String value) {
        if (value == null || value.isBlank() || value.length() > 1024 || value.chars().anyMatch(Character::isISOControl)) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private String digest(ReconciliationActor actor, String... fields) {
        var value = new StringBuilder("reconciliation-review-v1");
        field(value, actor.tenantId());
        field(value, actor.serviceId());
        for (String field : fields) {
            field(value, field);
        }
        return DigestUtils.sha256(value.toString());
    }

    private void field(StringBuilder value, String field) {
        if (field == null) {
            value.append("-1:");
        } else {
            value.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
    }

    private record Evidence(ReconciliationReviewReference reference, StoredReconciliationDifference difference,
                            ReconciliationScope scope) {
        @Override
        public String toString() {
            return io.github.loadup.commons.json.ToStringAsJson.reflectionToString(this);
        }
    }
}
