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
import io.github.loadup.modules.transfer.client.enums.TransferKind;
import io.github.loadup.modules.transfer.client.enums.TransferStatus;
import io.github.loadup.modules.transfer.client.facade.TransferTaskFacade;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationImportConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationStatementParser;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationChannelImportCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationImportRetryCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationImportDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationSourceFileDTO;
import io.github.loadup.pay.reconciliation.client.facade.ReconciliationImportFacade;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IMPORT_IN_PROGRESS;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class ReconciliationImportService implements ReconciliationImportFacade {
    private final ReconciliationActorProvider actors;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationImportGateway imports;
    private final ReconciliationSourceFiles files;
    private final ReconciliationStatementParser parser;
    private final ReconciliationTransactions transactions;
    private final TransferTaskFacade tasks;
    private final ReconciliationImportConverter converter;
    private final IdGenerator ids;
    private final Validator validator;
    private final ReconciliationOfficialImports official;

    public ReconciliationImportService(ReconciliationActorProvider actors, ReconciliationAccountDirectory accounts,
                                       ReconciliationBatchGateway batches, ReconciliationImportGateway imports, ReconciliationSourceFiles files,
                                       ReconciliationStatementParser parser, ReconciliationTransactions transactions, TransferTaskFacade tasks,
                                       ReconciliationImportConverter converter, IdGenerator ids, Validator validator, ReconciliationOfficialImports official) {
        this.actors = actors;
        this.accounts = accounts;
        this.batches = batches;
        this.imports = imports;
        this.files = files;
        this.parser = parser;
        this.transactions = transactions;
        this.tasks = tasks;
        this.converter = converter;
        this.ids = ids;
        this.validator = validator;
        this.official = official;
    }

    @Override
    public ReconciliationSourceFileDTO upload(String filename, String type, long size, InputStream content) {
        var actor = actor("upload");
        return converter.toDTO(files.upload(actor.tenantId(), actor.serviceId(), filename, type, size, content));
    }

    @Override
    public ReconciliationImportDTO submit(ReconciliationChannelImportCommand command) {
        validate(command);
        var actor = actor("submit");
        if (command.requestKey().startsWith("official:")) throw new CommonException(INVALID_ARGUMENT);
        var digest = digest(actor, command);
        var existing = transactions.readSnapshot(actor.tenantId(), () -> imports.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey())
                .map(value -> replay(actor, value, digest)));
        if (existing.isPresent()) return existing.get();
        var original = transactions.readSnapshot(actor.tenantId(), () -> authorizedBatch(actor, command.batchId(), false));
        available(original);
        var account = accounts.statementAccount(actor.tenantId(), actor.serviceId(), original.channelAccountCode(), original.batch().scope());
        if (!parser.supports(original.batch().scope())) throw new CommonException(INPUT_INVALID);
        var source = files.inspect(actor.tenantId(), actor.serviceId(), command.fileId());
        if (!source.sha256().equals(command.sourceSha256())) throw new CommonException(INPUT_INVALID);
        return transactions.writeLocal(actor.tenantId(), () -> {
            var current = authorizedBatch(actor, command.batchId(), true);
            var raced = imports.findRequest(actor.tenantId(), actor.serviceId(), command.requestKey());
            if (raced.isPresent()) return replay(actor, raced.get(), digest);
            available(current);
            if (!original.batch().scope().equals(current.batch().scope()) || !account.equals(accounts.statementAccount(actor.tenantId(), actor.serviceId(),
                    current.channelAccountCode(), current.batch().scope()))) throw new CommonException(SCOPE_MISMATCH);
            if (imports.activeForBatch(actor.tenantId(), command.batchId()).isPresent())
                throw new CommonException(IMPORT_IN_PROGRESS);
            var id = ids.generate();
            files.attach(actor.tenantId(), actor.serviceId(), source, id);
            var task = nativeCall(() -> tasks.submit(actor.tenantId(), actor.serviceId(), TransferKind.IMPORT,
                    ReconciliationImportExecutionService.HANDLER_KEY, source.fileId(), Map.of("importId", id)));
            if (task == null) throw new CommonException(SNAPSHOT_INVALID);
            var request = ReconciliationImportRequest.init(id, command.batchId(), actor.serviceId(), command.requestKey(), digest,
                    current.batch().scope(), account, source, task.id());
            ReconciliationImportExecutionService.validateTask(task, request, TransferStatus.QUEUED);
            imports.insert(actor.tenantId(), request);
            return converter.toDTO(request, task.status().name());
        });
    }

    @Override
    public ReconciliationImportDTO detail(IdQuery query) {
        validate(query);
        var actor = actor("read");
        return transactions.readSnapshot(actor.tenantId(), () -> {
            var value = checked(actor, imports.find(actor.tenantId(), query.id()).orElseThrow(() -> new CommonException(NOT_FOUND)), false);
            return dto(actor.tenantId(), value);
        });
    }

    @Override
    public ReconciliationImportDTO retry(ReconciliationImportRetryCommand command) {
        validate(command);
        var actor = actor("retry");
        var original = transactions.readSnapshot(actor.tenantId(), () -> checked(actor,
                imports.find(actor.tenantId(), command.importId()).orElseThrow(() -> new CommonException(NOT_FOUND)), false));
        return transactions.writeLocal(actor.tenantId(), () -> {
            authorizedBatch(actor, original.batchId(), true);
            var value = checked(actor, imports.lock(actor.tenantId(), original.id()).orElseThrow(() -> new CommonException(NOT_FOUND)), false);
            if (!value.taskId().equals(command.expectedTaskId())) throw new CommonException(IDEMPOTENCY_CONFLICT);
            if (value.state() == ReconciliationImportState.REJECTED) throw new CommonException(INPUT_INVALID);
            var task = nativeCall(() -> tasks.get(actor.tenantId(), value.taskId(), actor.serviceId(), false));
            ReconciliationImportExecutionService.validateTask(task, value, null);
            if (task.status() == TransferStatus.QUEUED || task.status() == TransferStatus.SUCCEEDED && value.state() == ReconciliationImportState.SEALED)
                return converter.toDTO(value, task.status().name());
            if (task.status() != TransferStatus.FAILED) throw new CommonException(INVALID_STATE);
            files.verify(actor.tenantId(), actor.serviceId(), value.file(), value.id());
            task = nativeCall(() -> tasks.retry(actor.tenantId(), value.taskId(), actor.serviceId(), false));
            ReconciliationImportExecutionService.validateTask(task, value, TransferStatus.QUEUED);
            return converter.toDTO(value, task.status().name());
        });
    }

    private ReconciliationImportDTO replay(ReconciliationActor actor, ReconciliationImportRequest value, String digest) {
        checked(actor, value, false);
        if (!value.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return dto(actor.tenantId(), value);
    }

    private ReconciliationImportDTO dto(String tenant, ReconciliationImportRequest value) {
        var task = nativeCall(() -> tasks.get(tenant, value.taskId(), value.callerServiceId(), false));
        ReconciliationImportExecutionService.validateTask(task, value, null);
        if (task.status() == TransferStatus.SUCCEEDED && value.state() != ReconciliationImportState.SEALED)
            throw new CommonException(SNAPSHOT_INVALID);
        return converter.toDTO(value, task.status().name());
    }

    private ReconciliationImportRequest checked(ReconciliationActor actor, ReconciliationImportRequest value, boolean lock) {
        if (!value.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        var batch = authorizedBatch(actor, value.batchId(), lock);
        if (!value.scope().equals(batch.batch().scope()) || !value.statementAccount().equals(accounts.statementAccount(actor.tenantId(), actor.serviceId(),
                batch.channelAccountCode(), batch.batch().scope()))) throw new CommonException(SCOPE_MISMATCH);
        official.verify(actor.tenantId(), value);
        return value;
    }

    private StoredReconciliationBatch authorizedBatch(ReconciliationActor actor, String id, boolean lock) {
        var value = (lock ? batches.lock(actor.tenantId(), id) : batches.find(actor.tenantId(), id)).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!value.callerServiceId().equals(actor.serviceId())) throw new CommonException(ACTION_FORBIDDEN);
        accounts.authorize(actor.tenantId(), actor.serviceId(), value.channelAccountCode(), value.batch().scope());
        return value;
    }

    private void available(StoredReconciliationBatch value) {
        if (value.batch().state() != ReconciliationState.INIT || value.batch().channel() != null)
            throw new CommonException(INVALID_STATE);
    }

    private ReconciliationActor actor(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var actor = actors.current();
        if (actor == null || !actor.permissions().contains("pay:reconciliation:import:" + operation))
            throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private String digest(ReconciliationActor actor, ReconciliationChannelImportCommand command) {
        var framed = new StringBuilder();
        for (var part : java.util.List.of("reconciliation-manual-import-v1", actor.tenantId(), actor.serviceId(), command.requestKey(), command.batchId(), command.fileId(), command.sourceSha256()))
            framed.append(part.getBytes(StandardCharsets.UTF_8).length).append(':').append(part);
        return DigestUtils.sha256(framed.toString());
    }

    private <T> T nativeCall(Supplier<T> action) {
        try {
            return action.get();
        } catch (CommonException failure) {
            if (failure.getResultCode() instanceof io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum)
                throw failure;
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
