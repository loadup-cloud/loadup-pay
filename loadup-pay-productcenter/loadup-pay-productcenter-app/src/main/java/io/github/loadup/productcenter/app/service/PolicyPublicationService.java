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
package io.github.loadup.productcenter.app.service;

import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.CONTRACT_NOT_ADMITTING;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.INVALID_STATE;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.NOT_FOUND;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.REPLAY_NOT_ALLOWED;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.productcenter.app.converter.PolicyPublicationConverter;
import io.github.loadup.productcenter.app.model.PolicyActivationDispatch;
import io.github.loadup.productcenter.app.model.limitcenter.dto.LimitPolicyActivationDTO;
import io.github.loadup.productcenter.app.port.CurrentContractGateway;
import io.github.loadup.productcenter.app.port.PolicyActivationClient;
import io.github.loadup.productcenter.app.port.PolicyPublicationCodec;
import io.github.loadup.productcenter.app.security.PublicationActor;
import io.github.loadup.productcenter.app.security.PublicationActorProvider;
import io.github.loadup.productcenter.client.command.LimitPolicyPublishCommand;
import io.github.loadup.productcenter.client.command.LimitPolicyReplayCommand;
import io.github.loadup.productcenter.client.dto.LimitPolicyPublicationDTO;
import io.github.loadup.productcenter.client.facade.LimitPolicyPublicationFacade;
import io.github.loadup.productcenter.domain.gateway.PolicyPublicationGateway;
import io.github.loadup.productcenter.domain.model.ContractStatus;
import io.github.loadup.productcenter.domain.model.PolicyPublication;
import io.github.loadup.productcenter.domain.policy.CommercialPolicyParser;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PolicyPublicationService implements LimitPolicyPublicationFacade {
    private final CommercialPolicyParser policies = new CommercialPolicyParser();
    private final PublicationActorProvider actors;
    private final Validator validator;
    private final PublicationTransactions transactions;
    private final CurrentContractGateway contracts;
    private final PolicyPublicationGateway publications;
    private final LimitPolicyCompiler compiler;
    private final PolicyPublicationCodec codec;
    private final PolicyActivationClient client;
    private final TaskTemplate outbox;
    private final PolicyPublicationConverter converter;
    private final IdGenerator ids;
    private final Clock clock;

    public PolicyPublicationService(
            PublicationActorProvider actors,
            Validator validator,
            PublicationTransactions transactions,
            CurrentContractGateway contracts,
            PolicyPublicationGateway publications,
            LimitPolicyCompiler compiler,
            PolicyPublicationCodec codec,
            PolicyActivationClient client,
            TaskTemplate outbox,
            PolicyPublicationConverter converter,
            IdGenerator ids,
            Clock clock) {
        this.actors = actors;
        this.validator = validator;
        this.transactions = transactions;
        this.contracts = contracts;
        this.publications = publications;
        this.compiler = compiler;
        this.codec = codec;
        this.client = client;
        this.outbox = outbox;
        this.converter = converter;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public LimitPolicyPublicationDTO publish(LimitPolicyPublishCommand command) {
        var actor = authorize(command, "pay:product:publish");
        var stored = transactions.writeLocal(() -> {
            DomainChecks.id(command.payeeMerchantId());
            DomainChecks.text(command.productScopeKey(), 128);
            DomainChecks.id(command.expectedContractId());
            DomainChecks.text(command.expectedSnapshotHash(), 128);
            var prior = publications.findByContract(command.expectedContractId(), command.expectedRevision());
            if (prior.isPresent()) {
                return replayOriginal(prior.get(), command);
            }
            var header = contracts
                    .lockByScope(command.payeeMerchantId(), command.productScopeKey())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!header.merchantId().equals(command.payeeMerchantId())
                    || !header.scopeKey().equals(command.productScopeKey())
                    || !header.id().equals(command.expectedContractId())) {
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            }
            // A contender must see the winner after waiting on the shared signed-contract row.
            prior = publications.findByContract(header.id(), command.expectedRevision());
            if (prior.isPresent()) {
                return replayOriginal(prior.get(), command);
            }
            if (header.status() != ContractStatus.NORMAL) {
                throw new CommonException(CONTRACT_NOT_ADMITTING);
            }
            var snapshot = contracts.snapshot(header);
            if (snapshot.revision() != command.expectedRevision()
                    || !snapshot.snapshotHash().equals(command.expectedSnapshotHash())) {
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            }
            if (snapshot.effectiveTo() != null && !clock.instant().isBefore(snapshot.effectiveTo())) {
                throw new CommonException(CONTRACT_NOT_ADMITTING);
            }
            var selected = snapshot.items().values().stream()
                    .filter(item -> item.configuration().values().containsKey("trade.amountModel"))
                    .toList();
            if (selected.size() != 1) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            var activation = compiler.compile(
                    command.payeeMerchantId(),
                    policies.parse(selected.getFirst().configuration()).limits());
            if (publications.findByMerchant(command.payeeMerchantId()).isPresent()
                    || publications.findByPolicy(activation.policyId()).isPresent()) {
                throw new CommonException(POLICY_PUBLICATION_CONFLICT);
            }
            var now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            var initial = new PolicyPublication(
                    ids.generate(),
                    header.id(),
                    snapshot.revision(),
                    snapshot.snapshotHash(),
                    command.payeeMerchantId(),
                    command.productScopeKey(),
                    activation.policyId(),
                    activation.policyVersion(),
                    activation.policyHash(),
                    codec.encode(activation),
                    "INIT",
                    null,
                    now,
                    null,
                    1,
                    0);
            publications.insert(initial);
            String event = outbox.submit(TaskSubmission.immediate(
                    PolicyActivationDispatch.TYPE,
                    initial.id(),
                    JsonUtil.toJson(new PolicyActivationDispatch(initial.id()))));
            var publishing = initial.publishing(event);
            publications.update(publishing, initial.rowVersion());
            return publishing;
        });
        return transactions.guarded(() -> present(stored));
    }

    private PolicyPublication replayOriginal(PolicyPublication stored, LimitPolicyPublishCommand command) {
        stored.requireSource(
                command.expectedContractId(),
                command.expectedRevision(),
                command.expectedSnapshotHash(),
                command.payeeMerchantId(),
                command.productScopeKey());
        codec.decode(stored);
        return stored;
    }

    @Override
    public LimitPolicyPublicationDTO detail(IdQuery query) {
        var actor = authorize(query, "pay:product:publication:read");
        return transactions.guarded(() -> {
            DomainChecks.id(query.id());
            var stored = publications.findById(query.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
            codec.decode(stored);
            return present(stored);
        });
    }

    @Override
    public LimitPolicyPublicationDTO replay(LimitPolicyReplayCommand command) {
        var actor = authorize(command, "pay:product:publication:replay");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        return transactions.guarded(() -> {
            DomainChecks.id(command.publicationId());
            DomainChecks.text(command.reason(), 512);
            var stored =
                    publications.findById(command.publicationId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            codec.decode(stored);
            var queue = queue(stored).orElseThrow(() -> new CommonException(REPLAY_NOT_ALLOWED));
            if (queue.status() != TaskStatus.FAILED) {
                throw new CommonException(REPLAY_NOT_ALLOWED);
            }
            try {
                outbox.retryFailed(stored.outboxEventId(), actor.principalId(), command.reason());
            } catch (CommonException failure) {
                if (failure.getResultCode() == io.github.loadup.components.task.TaskResultCodeEnum.INVALID_STATE) {
                    throw new CommonException(REPLAY_NOT_ALLOWED);
                }
                throw failure;
            }
            return present(publications.findById(stored.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
        });
    }

    /**
     * Trusted Outbox handler entry point; no user authentication is reconstructed from queued data.
     */
    public void deliver(String eventId, String publicationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var stored = transactions.guarded(
                () -> publications.findById(publicationId).orElseThrow(() -> new CommonException(NOT_FOUND)));
        if (!publicationId.equals(stored.id()) || !eventId.equals(stored.outboxEventId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var command = codec.decode(stored);
        if ("ACTIVE".equals(stored.state())) {
            return;
        }
        var ack = client.activate(command); // Network I/O is outside every local transaction.
        validateAck(stored, ack);
        LocalDateTime activatedAt = BusinessTime.local(ack.activatedAt());
        transactions.writeLocal(() -> {
            var current = publications.lockById(publicationId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            current.requireSameIntent(stored);
            var active = current.acknowledge(eventId, activatedAt);
            if (active != current) {
                publications.update(active, current.rowVersion());
            }
            return active;
        });
    }

    private void validateAck(PolicyPublication stored, LimitPolicyActivationDTO ack) {
        if (ack == null
                || !stored.policyId().equals(ack.policyId())
                || stored.policyVersion() != ack.policyVersion()
                || !stored.policyHash().equals(ack.policyHash())
                || ack.activatedAt() == null
                || ack.activatedAt().getNano() % 1000 != 0
                || ack.activatedAt().getYear() < 1970
                || ack.activatedAt().getYear() >= 9999) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private LimitPolicyPublicationDTO present(PolicyPublication stored) {
        var queue = queue(stored);
        if (queue.isPresent()) {
            var task = queue.get();
            return converter.toDTO(stored, task.status().name(), task.attempts());
        }
        // The worker commits the business ACK before deleting the task. Refresh a possibly older
        // business read.
        var latest = publications.findById(stored.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        latest.requireSameIntent(stored);
        if (!"ACTIVE".equals(latest.state())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return converter.toDTO(latest, TaskStatus.SUCCEEDED.name(), null);
    }

    private Optional<TaskExecution> queue(PolicyPublication stored) {
        return outbox.find(stored.outboxEventId()).map(task -> {
            if (!stored.id().equals(task.bizId())
                    || !stored.outboxEventId().equals(task.id())
                    || !PolicyActivationDispatch.TYPE.equals(task.taskType())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return task;
        });
    }

    private PublicationActor authorize(Object command, String permission) {
        if (command == null || !validator.validate(command).isEmpty()) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var actor = actors.current();
        if (!actor.permissions().contains(permission)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        return actor;
    }
}
