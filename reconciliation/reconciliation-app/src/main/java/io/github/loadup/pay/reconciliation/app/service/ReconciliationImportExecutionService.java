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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.modules.transfer.client.dto.TransferTaskDTO;
import io.github.loadup.modules.transfer.client.enums.TransferKind;
import io.github.loadup.modules.transfer.client.enums.TransferStatus;
import io.github.loadup.modules.transfer.client.facade.TransferTaskFacade;
import io.github.loadup.modules.transfer.client.spi.TransferContext;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationStatementParser;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.util.Objects;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IMPORT_SUPERSEDED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Service
public class ReconciliationImportExecutionService {
    public static final String HANDLER_KEY = "pay.reconciliation.channel-import";
    private final ObjectProvider<TransferTaskFacade> tasks;
    private final ReconciliationImportGateway imports;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationSourceFiles files;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationStatementParser parser;
    private final ReconciliationTransactions transactions;
    private final ReconciliationConverter converter;
    private final ReconciliationOfficialImports official;

    public ReconciliationImportExecutionService(ObjectProvider<TransferTaskFacade> tasks, ReconciliationImportGateway imports,
                                                ReconciliationBatchGateway batches, ReconciliationInputGateway inputs, ReconciliationSourceFiles files,
                                                ReconciliationAccountDirectory accounts, ReconciliationStatementParser parser, ReconciliationTransactions transactions,
                                                ReconciliationConverter converter, ReconciliationOfficialImports official) {
        this.tasks = tasks;
        this.imports = imports;
        this.batches = batches;
        this.inputs = inputs;
        this.files = files;
        this.accounts = accounts;
        this.parser = parser;
        this.transactions = transactions;
        this.converter = converter;
        this.official = official;
    }

    static void validateTask(TransferTaskDTO task, ReconciliationImportRequest request, TransferStatus expected) {
        if (task == null || !Objects.equals(task.id(), request.taskId()) || !Objects.equals(task.ownerId(), request.callerServiceId())
                || task.kind() != TransferKind.IMPORT
                || !HANDLER_KEY.equals(task.handlerKey()) || !Objects.equals(task.sourceFileId(), request.file().fileId())
                || task.status() == null || expected != null && task.status() != expected) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    public void handle(TransferContext context, InputStream content) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || context == null
                || context.taskId() == null || context.ownerId() == null
                || context.options() == null || context.options().size() != 1 || context.options().get("importId") == null || content == null) {
            throw new CommonException(INVALID_STATE);
        }
        String tenant = TenantUtil.getTenantId();
        var request = transactions.readSnapshot(tenant, () -> {
            var value = imports.find(tenant, context.options().get("importId")).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!context.taskId().equals(value.taskId()) || !context.ownerId().equals(value.callerServiceId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            validateTask(tasks.getObject().get(value.taskId(), value.callerServiceId(), false), value, TransferStatus.RUNNING);
            var batch = authorized(tenant, value, false);
            if (value.state() == ReconciliationImportState.SEALED) {
                var manifest = inputs.sealedManifest(tenant, value.artifactId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                value.seal(manifest);
                if (!manifest.equals(batch.batch().channel())) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
            }
            official.verify(tenant, value);
            files.verify(tenant, value.callerServiceId(), value.file(), value.id());
            return value;
        });
        if (request.state() == ReconciliationImportState.SEALED) {
            return;
        }
        if (request.state() == ReconciliationImportState.REJECTED) {
            throw new CommonException(INPUT_INVALID);
        }
        try {
            var data = parser.parse(content, request);
            if (!request.file().sha256().equals(data.manifest().artifactSha256())) {
                throw new CommonException(INPUT_INVALID);
            }
            // The content-addressed file, sealed rows, batch reference and logical outcome commit together.
            transactions.writeLocal(tenant, () -> {
                var batch = authorized(tenant, request, true);
                var current = imports.lock(tenant, request.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!request.equals(current) && current.state() != ReconciliationImportState.SEALED) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var sealed = current.seal(data.manifest());
                if (sealed == current) {
                    var manifest = inputs.sealedManifest(tenant, current.artifactId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    if (!manifest.equals(data.manifest()) || !manifest.equals(batch.batch().channel())) {
                        throw new CommonException(SNAPSHOT_INVALID);
                    }
                    return false;
                }
                if (batch.batch().channel() != null && !data.manifest().equals(batch.batch().channel())) {
                    throw new CommonException(IMPORT_SUPERSEDED);
                }
                official.verify(tenant, current);
                files.verify(tenant, current.callerServiceId(), current.file(), current.id());
                inputs.insertSealed(tenant, current.file().fileId(), data);
                var next = batch.batch().attach(data.manifest());
                batches.update(tenant, converter.withBatch(batch, next), batch.batch().version());
                imports.update(tenant, sealed, current.version());
                return true;
            });
            context.report(data.records().size(), data.records().size());
        } catch (CommonException failure) {
            if (failure.getResultCode() == INPUT_INVALID || failure.getResultCode() == IMPORT_SUPERSEDED) {
                transactions.writeLocal(tenant, () -> {
                    authorized(tenant, request, true);
                    var current = imports.lock(tenant, request.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    if (current.state() == ReconciliationImportState.INIT) {
                        imports.update(tenant, current.reject(failure.getResultCode().getCode()), current.version());
                    }
                    return true;
                });
            }
            throw failure;
        }
    }

    private StoredReconciliationBatch authorized(String tenant, ReconciliationImportRequest request, boolean lock) {
        var batch = (lock ? batches.lock(tenant, request.batchId()) : batches.find(tenant, request.batchId())).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!request.callerServiceId().equals(batch.callerServiceId()) || !request.scope().equals(batch.batch().scope())
                || !request.statementAccount().equals(accounts.statementAccount(tenant, request.callerServiceId(), batch.channelAccountCode(), request.scope()))) {
            throw new CommonException(SCOPE_MISMATCH);
        }
        return batch;
    }
}
