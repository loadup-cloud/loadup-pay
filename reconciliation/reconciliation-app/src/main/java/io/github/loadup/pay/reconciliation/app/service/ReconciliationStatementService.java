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
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationStatementConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationStatementParser;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementSubmitCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationStatementDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationStatementFacade;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IMPORT_IN_PROGRESS;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class ReconciliationStatementService implements ReconciliationStatementFacade {
    private final ReconciliationActorProvider actors;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationStatementGateway sources;
    private final ReconciliationImportGateway imports;
    private final ReconciliationStatementParser parser;
    private final ReconciliationTransactions transactions;
    private final DelayedTaskTemplate tasks;
    private final ReconciliationStatementConverter converter;
    private final ReconciliationOfficialImports official;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final int maxBytes;

    public ReconciliationStatementService(ReconciliationActorProvider actors, ReconciliationAccountDirectory accounts, ReconciliationBatchGateway batches,
                                          ReconciliationStatementGateway sources, ReconciliationImportGateway imports, ReconciliationStatementParser parser,
                                          ReconciliationTransactions transactions, DelayedTaskTemplate tasks, ReconciliationStatementConverter converter,
                                          ReconciliationOfficialImports official, IdGenerator ids, Validator validator, @Qualifier("reconciliationClock") Clock clock,
                                          @Value("${loadup.modules.reconciliation.statement-max-bytes:16777216}") int maxBytes) {
        if (maxBytes < 32768 || maxBytes > 16777216) throw new CommonException(INVALID_ARGUMENT);
        this.actors = actors;
        this.accounts = accounts;
        this.batches = batches;
        this.sources = sources;
        this.imports = imports;
        this.parser = parser;
        this.transactions = transactions;
        this.tasks = tasks;
        this.converter = converter;
        this.official = official;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.maxBytes = maxBytes;
    }

    static void task(DelayedTask task, String tenant, ReconciliationStatementRequest value) {
        if (task == null || !value.taskId().equals(task.id()) || !tenant.equals(task.tenantId()) || !value.id().equals(task.bizId())
                || !ReconciliationStatementExecutionService.TYPE.equals(task.taskType()) || task.status() == null
                || task.attempts() < 0 || task.maxAttempts() < 1 || task.attempts() > task.maxAttempts()
                || task.status() == DelayedTaskStatus.SUCCEEDED && value.state() == ReconciliationStatementState.INIT)
            throw new CommonException(SNAPSHOT_INVALID);
    }

    @Override
    public ReconciliationStatementDTO submit(ReconciliationStatementSubmitCommand command) {
        validate(command);
        var actor = actor("submit");
        String digest = digest(actor, command);
        return transactions.writeLocal(actor.tenantId(), () -> {
            var batch = batch(actor, command.batchId(), true);
            var old = sources.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (old.isPresent()) {
                var value = old.get();
                checked(actor, value);
                if (!value.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
                return present(actor.tenantId(), value);
            }
            if (batch.batch().state() != ReconciliationState.INIT || batch.batch().channel() != null)
                throw new CommonException(INVALID_STATE);
            if (!parser.supports(batch.batch().scope()) || !batch.batch().scope().billDate().isBefore(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDate()))
                throw new CommonException(INPUT_INVALID);
            if (sources.activeForBatch(actor.tenantId(), command.batchId()).isPresent() || imports.activeForBatch(actor.tenantId(), command.batchId()).isPresent())
                throw new CommonException(IMPORT_IN_PROGRESS);
            var account = accounts.statementAccount(actor.tenantId(), actor.serviceId(), batch.channelAccountCode(), batch.batch().scope());
            var binding = accounts.statementBindingRevision(actor.tenantId(), actor.serviceId(), batch.channelAccountCode(), batch.batch().scope());
            var id = ids.generate();
            tasks.register(ReconciliationStatementExecutionService.TYPE, id, LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
            var task = tasks.find(ReconciliationStatementExecutionService.TYPE, id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var request = ReconciliationStatementRequest.init(id, command.batchId(), actor.serviceId(), command.requestKey(), digest,
                    batch.batch().scope(), account, binding, maxBytes, task.id());
            task(task, actor.tenantId(), request);
            if (task.status() != DelayedTaskStatus.PENDING) throw new CommonException(SNAPSHOT_INVALID);
            sources.insert(actor.tenantId(), request);
            return converter.toDTO(request, task);
        });
    }

    @Override
    public ReconciliationStatementDTO detail(IdQuery query) {
        validate(query);
        var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> present(actor.tenantId(), checked(actor, sources.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND)))));
    }

    @Override
    public ReconciliationStatementDTO retry(ReconciliationStatementRetryCommand command) {
        validate(command);
        var actor = actor("retry");
        var original = transactions.readSnapshot(actor.tenantId(), () -> checked(actor, sources.find(actor.tenantId(), command.sourceId()).orElseThrow(() -> new CommonException(NOT_FOUND))));
        return transactions.writeLocal(actor.tenantId(), () -> {
            batch(actor, original.batchId(), true);
            var value = checked(actor, sources.lock(actor.tenantId(), original.id()).orElseThrow(() -> new CommonException(NOT_FOUND)));
            var task = task(actor.tenantId(), value);
            if (!command.expectedTaskId().equals(task.id())) throw new CommonException(IDEMPOTENCY_CONFLICT);
            if (task.status() == DelayedTaskStatus.CANCELLED) throw new CommonException(INVALID_STATE);
            if (task.status() == DelayedTaskStatus.FAILED)
                tasks.retryFailed(ReconciliationStatementExecutionService.TYPE, value.id());
            return present(actor.tenantId(), value);
        });
    }

    private ReconciliationStatementRequest checked(ReconciliationActor actor, ReconciliationStatementRequest value) {
        if (!actor.serviceId().equals(value.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        var batch = batch(actor, value.batchId(), false);
        if (!value.scope().equals(batch.batch().scope()) || !value.statementAccount().equals(accounts.statementAccount(actor.tenantId(), actor.serviceId(), batch.channelAccountCode(), value.scope())))
            throw new CommonException(SCOPE_MISMATCH);
        return value;
    }

    private StoredReconciliationBatch batch(ReconciliationActor actor, String id, boolean lock) {
        var value = (lock ? batches.lock(actor.tenantId(), id) : batches.find(actor.tenantId(), id)).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!actor.serviceId().equals(value.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        accounts.authorize(actor.tenantId(), actor.serviceId(), value.channelAccountCode(), value.batch().scope());
        return value;
    }

    private ReconciliationStatementDTO present(String tenant, ReconciliationStatementRequest value) {
        if (value.state() == ReconciliationStatementState.READY) {
            var imported = imports.find(tenant, value.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            value.verifyImport(imported);
            official.verify(tenant, imported);
        }
        return converter.toDTO(value, task(tenant, value));
    }

    private DelayedTask task(String tenant, ReconciliationStatementRequest value) {
        var task = tasks.find(ReconciliationStatementExecutionService.TYPE, value.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        task(task, tenant, value);
        return task;
    }

    private ReconciliationActor actor(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor == null || !actor.permissions().contains("pay:reconciliation:statement:" + operation))
            throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private String digest(ReconciliationActor actor, ReconciliationStatementSubmitCommand command) {
        var framed = new StringBuilder();
        for (var field : List.of("official-statement-request-v1", actor.tenantId(), actor.serviceId(), command.batchId(), command.requestKey()))
            framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return DigestUtils.sha256(framed.toString());
    }
}
