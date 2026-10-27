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
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.port.*;
import io.github.loadup.pay.reconciliation.app.security.*;
import io.github.loadup.pay.reconciliation.client.command.*;
import io.github.loadup.pay.reconciliation.client.dto.*;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationFacade;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationDifferenceQuery;
import io.github.loadup.pay.reconciliation.domain.gateway.*;
import io.github.loadup.pay.reconciliation.domain.model.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

@Service
public class ReconciliationService implements ReconciliationFacade {
    private final ReconciliationActorProvider actors;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationDifferenceReader differences;
    private final ReconciliationTransactions transactions;
    private final ReconciliationConverter converter;
    private final DelayedTaskTemplate tasks;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    public ReconciliationService(ReconciliationActorProvider actors, ReconciliationAccountDirectory accounts,
            ReconciliationBatchGateway batches, ReconciliationInputGateway inputs, ReconciliationDifferenceReader differences,
            ReconciliationTransactions transactions, ReconciliationConverter converter, DelayedTaskTemplate tasks,
            IdGenerator ids, Validator validator, @Qualifier("reconciliationClock") Clock clock) {
        this.actors = actors; this.accounts = accounts; this.batches = batches; this.inputs = inputs; this.differences = differences;
        this.transactions = transactions; this.converter = converter; this.tasks = tasks; this.ids = ids; this.validator = validator; this.clock = clock;
    }
    @Override public ReconciliationBatchDTO create(ReconciliationBatchCreateCommand command) {
        validate(command); var actor = actor("create");
        var digest = digest(actor, command);
        var existing = transactions.readSnapshot(actor.tenantId(), () -> batches.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey()));
        if (existing.isPresent()) return replay(actor, existing.get(), digest);
        var scope = accounts.resolve(actor.tenantId(), actor.serviceId(), command.channelAccountCode(), command.billDate());
        return transactions.writeLocal(actor.tenantId(), () -> {
            var raced = batches.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (raced.isPresent()) return replay(actor, raced.get(), digest);
            var root = ReconciliationBatch.init(ids.generate(), scope);
            var stored = converter.toStored(root, actor.serviceId(), command.requestKey(), digest, command.channelAccountCode());
            batches.insert(actor.tenantId(), stored);
            return converter.toDTO(root);
        });
    }
    @Override public ReconciliationBatchDTO attach(ReconciliationInputAttachCommand command) {
        validate(command); var actor = actor("attach");
        return transactions.writeLocal(actor.tenantId(), () -> {
            var stored = checked(actor, batches.lock(actor.tenantId(), command.batchId()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            var input = inputs.sealedManifest(actor.tenantId(), command.artifactId()).orElseThrow(() -> new CommonException(INPUT_NOT_SEALED));
            var side = converter.toSide(command.side());
            if (input.side() != side || !stored.batch().scope().equals(input.scope())) throw new CommonException(SCOPE_MISMATCH);
            var prior = side == ReconciliationSide.CHANNEL ? stored.batch().channel() : stored.batch().ledger();
            if (prior != null) {
                if (!prior.equals(input)) throw new CommonException(IDEMPOTENCY_CONFLICT);
                return converter.toDTO(stored.batch());
            }
            var next = stored.batch().attach(input);
            batches.update(actor.tenantId(), converter.withBatch(stored, next), stored.batch().version());
            return converter.toDTO(next);
        });
    }
    @Override public ReconciliationBatchDTO start(IdQuery query) {
        validate(query); var actor = actor("start");
        return transactions.writeLocal(actor.tenantId(), () -> {
            var stored = checked(actor, batches.lock(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            if (stored.batch().state() == ReconciliationState.RUNNING || terminal(stored.batch())) return converter.toDTO(stored.batch());
            if (stored.batch().state() != ReconciliationState.READY) throw new CommonException(INPUT_NOT_SEALED);
            var next = stored.batch().begin(ids.generate());
            batches.update(actor.tenantId(), converter.withBatch(stored, next), stored.batch().version());
            tasks.register(ReconciliationExecutionService.TYPE, next.runId(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
            return converter.toDTO(next);
        });
    }
    @Override public ReconciliationBatchDTO detail(IdQuery query) {
        validate(query); var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> converter.toDTO(checked(actor,
                batches.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND))).batch()));
    }
    @Override public PageDTO<ReconciliationDifferenceDTO> differences(ReconciliationDifferenceQuery query) {
        validate(query); var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> {
            var stored = checked(actor, batches.find(actor.tenantId(), query.batchId()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            if (!terminal(stored.batch())) throw new CommonException(INVALID_STATE);
            var page = differences.page(actor.tenantId(), query.batchId(), converter.toKind(query.kind()), converter.toReason(query.reason()), query.page(), query.size());
            if (page.records().size() > query.size() || page.total() > stored.batch().result().differenceCount() || query.kind() == null && query.reason() == null
                    && page.total() != stored.batch().result().differenceCount()) throw new CommonException(SNAPSHOT_INVALID);
            for (var row : page.records()) if (!row.batchId().equals(stored.batch().id()) || !row.runId().equals(stored.batch().runId())
                    || query.kind() != null && row.difference().key().kind() != converter.toKind(query.kind())
                    || query.reason() != null && !row.difference().reasons().contains(converter.toReason(query.reason())))
                throw new CommonException(SNAPSHOT_INVALID);
            return PageDTO.of(page.records().stream().map(converter::toDTO).toList(), page.total(), query.page(), query.size());
        });
    }
    private ReconciliationBatchDTO replay(ReconciliationActor actor, StoredReconciliationBatch stored, String digest) {
        checked(actor, stored);
        if (!stored.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toDTO(stored.batch());
    }
    private StoredReconciliationBatch checked(ReconciliationActor actor, StoredReconciliationBatch stored) {
        if (!stored.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        accounts.authorize(actor.tenantId(), actor.serviceId(), stored.channelAccountCode(), stored.batch().scope());
        return stored;
    }
    private ReconciliationActor actor(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor == null || !actor.permissions().contains("pay:reconciliation:batch:" + operation)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }
    private void validate(Object value) { if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT); }
    private boolean terminal(ReconciliationBatch batch) { return batch.state() == ReconciliationState.MATCHED || batch.state() == ReconciliationState.DIFFERENCES; }
    private String digest(ReconciliationActor actor, ReconciliationBatchCreateCommand command) {
        var value = new StringBuilder();
        for (String part : java.util.List.of("reconciliation-create-v1", actor.tenantId(), actor.serviceId(), command.requestKey(), command.channelAccountCode(), command.billDate().toString()))
            value.append(part.getBytes(StandardCharsets.UTF_8).length).append(':').append(part);
        return DigestUtils.sha256(value.toString());
    }
}
