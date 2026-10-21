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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.modules.contract.domain.model.ContractStatus;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.limitcenter.client.dto.LimitPolicyActivationDTO;
import io.github.loadup.pay.product.app.converter.PolicyPublicationConverter;
import io.github.loadup.pay.product.app.model.PolicyActivationDispatch;
import io.github.loadup.pay.product.app.port.CurrentContractGateway;
import io.github.loadup.pay.product.app.port.PolicyActivationClient;
import io.github.loadup.pay.product.app.port.PolicyPublicationCodec;
import io.github.loadup.pay.product.app.security.PublicationActor;
import io.github.loadup.pay.product.app.security.PublicationActorProvider;
import io.github.loadup.pay.product.client.command.LimitPolicyPublishCommand;
import io.github.loadup.pay.product.client.command.LimitPolicyReplayCommand;
import io.github.loadup.pay.product.client.dto.LimitPolicyPublicationDTO;
import io.github.loadup.pay.product.client.facade.LimitPolicyPublicationFacade;
import io.github.loadup.pay.product.domain.gateway.PolicyPublicationGateway;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.CONTRACT_NOT_ADMITTING;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.REPLAY_NOT_ALLOWED;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

@Service
public class PolicyPublicationService implements LimitPolicyPublicationFacade {
    private final PublicationActorProvider actors;
    private final Validator validator;
    private final PublicationTransactions transactions;
    private final CurrentContractGateway contracts;
    private final PolicyPublicationGateway publications;
    private final LimitPolicyCompiler compiler;
    private final PolicyPublicationCodec codec;
    private final PolicyActivationClient client;
    private final OutboxTemplate outbox;
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
            OutboxTemplate outbox,
            PolicyPublicationConverter converter,
            IdGenerator ids,
            @Qualifier("productClock") Clock clock) {
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
        var stored = transactions.write(actor.tenantId(), () -> {
            DomainChecks.id(command.payeeMerchantId());
            DomainChecks.text(command.productScopeKey(), 128);
            DomainChecks.id(command.expectedContractId());
            DomainChecks.text(command.expectedSnapshotHash(), 128);
            var prior = publications.findByContract(
                    actor.tenantId(), command.expectedContractId(), command.expectedRevision());
            if (prior.isPresent()) return replayOriginal(actor.tenantId(), prior.get(), command);
            var header = contracts
                    .lockByScope(actor.tenantId(), command.payeeMerchantId(), command.productScopeKey())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            if (!header.tenantId().equals(actor.tenantId())
                    || !header.merchantId().equals(command.payeeMerchantId())
                    || !header.scopeKey().equals(command.productScopeKey())
                    || !header.id().equals(command.expectedContractId()))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            // A contender must see the winner after waiting on the shared signed-contract row.
            prior = publications.findByContract(actor.tenantId(), header.id(), command.expectedRevision());
            if (prior.isPresent()) return replayOriginal(actor.tenantId(), prior.get(), command);
            if (header.status() != ContractStatus.NORMAL) throw new CommonException(CONTRACT_NOT_ADMITTING);
            var snapshot = contracts.snapshot(header);
            if (snapshot.revision() != command.expectedRevision()
                    || !snapshot.snapshotHash().equals(command.expectedSnapshotHash()))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            if (snapshot.effectiveTo() != null && !clock.instant().isBefore(snapshot.effectiveTo()))
                throw new CommonException(CONTRACT_NOT_ADMITTING);
            var selected = snapshot.items().values().stream()
                    .filter(item -> item.configuration().values().containsKey("trade.amountModel"))
                    .toList();
            if (selected.size() != 1) throw new CommonException(UNSUPPORTED_POLICY);
            var activation = compiler.compile(
                    actor.tenantId(),
                    command.payeeMerchantId(),
                    selected.getFirst().configuration());
            if (publications
                            .findByMerchant(actor.tenantId(), command.payeeMerchantId())
                            .isPresent()
                    || publications
                            .findByPolicy(actor.tenantId(), activation.policyId())
                            .isPresent()) throw new CommonException(POLICY_PUBLICATION_CONFLICT);
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
                    codec.encode(actor.tenantId(), activation),
                    "INIT",
                    null,
                    now,
                    null,
                    1,
                    0);
            publications.insert(actor.tenantId(), initial);
            String event = outbox.publish(new OutboxMessage(
                    actor.tenantId(),
                    PolicyActivationDispatch.TYPE,
                    1,
                    initial.id(),
                    JsonUtil.toJson(new PolicyActivationDispatch(initial.id()))));
            var publishing = initial.publishing(event);
            publications.update(actor.tenantId(), publishing, initial.rowVersion());
            return publishing;
        });
        return transactions.read(actor.tenantId(), () -> present(actor.tenantId(), stored));
    }

    private PolicyPublication replayOriginal(
            String tenant, PolicyPublication stored, LimitPolicyPublishCommand command) {
        stored.requireSource(
                command.expectedContractId(),
                command.expectedRevision(),
                command.expectedSnapshotHash(),
                command.payeeMerchantId(),
                command.productScopeKey());
        codec.decode(tenant, stored);
        return stored;
    }

    @Override
    public LimitPolicyPublicationDTO detail(IdQuery query) {
        var actor = authorize(query, "pay:product:publication:read");
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.id());
            var stored = publications
                    .findById(actor.tenantId(), query.id())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            codec.decode(actor.tenantId(), stored);
            return present(actor.tenantId(), stored);
        });
    }

    @Override
    public LimitPolicyPublicationDTO replay(LimitPolicyReplayCommand command) {
        var actor = authorize(command, "pay:product:publication:replay");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(command.publicationId());
            DomainChecks.text(command.reason(), 512);
            var stored = publications
                    .findById(actor.tenantId(), command.publicationId())
                    .orElseThrow(() -> new CommonException(NOT_FOUND));
            codec.decode(actor.tenantId(), stored);
            var queue = queue(actor.tenantId(), stored);
            // Framework replay uses its own audited transaction; never nest it inside a publication write transaction.
            if (!"FAILED".equals(queue.status())
                    || !outbox.replay(actor.tenantId(), stored.outboxEventId(), actor.principalId(), command.reason()))
                throw new CommonException(REPLAY_NOT_ALLOWED);
            return present(
                    actor.tenantId(),
                    publications
                            .findById(actor.tenantId(), stored.id())
                            .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
        });
    }
    /** Trusted Outbox handler entry point; no user authentication is reconstructed from queued data. */
    public void deliver(String tenant, String eventId, String publicationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!client.supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        var stored = transactions.read(
                tenant,
                () -> publications.findById(tenant, publicationId).orElseThrow(() -> new CommonException(NOT_FOUND)));
        if (!publicationId.equals(stored.id()) || !eventId.equals(stored.outboxEventId()))
            throw new CommonException(SNAPSHOT_INVALID);
        var command = codec.decode(tenant, stored);
        if ("ACTIVE".equals(stored.state())) return;
        var ack = client.activate(tenant, command); // Network I/O is outside every local transaction.
        validateAck(stored, ack);
        LocalDateTime activatedAt = BusinessTime.local(ack.activatedAt());
        transactions.write(tenant, () -> {
            var current = publications
                    .lockById(tenant, publicationId)
                    .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            current.requireSameIntent(stored);
            var active = current.acknowledge(eventId, activatedAt);
            if (active != current) publications.update(tenant, active, current.rowVersion());
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
                || ack.activatedAt().getYear() >= 9999) throw new CommonException(DEPENDENCY_UNAVAILABLE);
    }

    private LimitPolicyPublicationDTO present(String tenant, PolicyPublication stored) {
        var queue = queue(tenant, stored);
        if (!"ACTIVE".equals(stored.state()) && "SUCCEEDED".equals(queue.status())) {
            // A worker may commit the ACK and complete delivery between the two independent reads.
            var latest =
                    publications.findById(tenant, stored.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            latest.requireSameIntent(stored);
            if (!"ACTIVE".equals(latest.state())) throw new CommonException(SNAPSHOT_INVALID);
            return converter.toDTO(latest, queue.status(), queue.attempts());
        }
        return converter.toDTO(stored, queue.status(), queue.attempts());
    }

    private OutboxRecord queue(String tenant, PolicyPublication stored) {
        var queue =
                outbox.find(tenant, stored.outboxEventId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!tenant.equals(queue.tenantId())
                || !stored.id().equals(queue.businessId())
                || !stored.outboxEventId().equals(queue.id())
                || !PolicyActivationDispatch.TYPE.equals(queue.type())) throw new CommonException(SNAPSHOT_INVALID);
        return queue;
    }

    private PublicationActor authorize(Object command, String permission) {
        if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        var actor = actors.current();
        if (!actor.permissions().contains(permission) || !client.supportsTenant(actor.tenantId()))
            throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }
}
