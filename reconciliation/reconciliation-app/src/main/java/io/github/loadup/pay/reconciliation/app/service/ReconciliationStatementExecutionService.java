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
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.modules.transfer.client.enums.TransferKind;
import io.github.loadup.modules.transfer.client.enums.TransferStatus;
import io.github.loadup.modules.transfer.client.facade.TransferTaskFacade;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationStatementConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentStatements;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IMPORT_IN_PROGRESS;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Native delayed download followed by one same-DataSource File/proof/Transfer/import commit.
 */
@Service
public class ReconciliationStatementExecutionService {
    public static final String TYPE = "pay.reconciliation.statement";
    private final DelayedTaskTemplate tasks;
    private final TransferTaskFacade transfers;
    private final ReconciliationStatementGateway sources;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationImportGateway imports;
    private final ReconciliationAccountDirectory accounts;
    private final ReconciliationPaymentStatements payment;
    private final ReconciliationSourceFiles files;
    private final ReconciliationTransactions transactions;
    private final ReconciliationStatementConverter converter;
    private final ReconciliationOfficialImports official;
    private final Clock clock;

    public ReconciliationStatementExecutionService(DelayedTaskTemplate tasks, TransferTaskFacade transfers, ReconciliationStatementGateway sources,
                                                   ReconciliationBatchGateway batches, ReconciliationImportGateway imports, ReconciliationAccountDirectory accounts,
                                                   ReconciliationPaymentStatements payment, ReconciliationSourceFiles files, ReconciliationTransactions transactions,
                                                   ReconciliationStatementConverter converter, ReconciliationOfficialImports official, @Qualifier("reconciliationClock") Clock clock) {
        this.tasks = tasks;
        this.transfers = transfers;
        this.sources = sources;
        this.batches = batches;
        this.imports = imports;
        this.accounts = accounts;
        this.payment = payment;
        this.files = files;
        this.transactions = transactions;
        this.converter = converter;
        this.official = official;
        this.clock = clock;
    }

    public void handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        lease(task);
        var original = transactions.readSnapshot(task.tenantId(), () -> {
            var value = sources.find(task.tenantId(), task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            ReconciliationStatementService.task(task, task.tenantId(), value);
            authorized(task.tenantId(), value, false);
            if (value.state() == ReconciliationStatementState.READY) linked(task.tenantId(), value);
            return value;
        });
        if (original.state() != ReconciliationStatementState.INIT) return;
        // Short transaction can terminate a superseded request before any network/file IO.
        boolean pending = transactions.writeLocal(task.tenantId(), () -> {
            var batch = authorized(task.tenantId(), original, true);
            var current = sources.lock(task.tenantId(), original.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (current.state() != ReconciliationStatementState.INIT) {
                if (current.state() == ReconciliationStatementState.READY) linked(task.tenantId(), current);
                return false;
            }
            if (!original.equals(current)) throw new CommonException(SNAPSHOT_INVALID);
            if (batch.batch().channel() != null || batch.batch().state() != ReconciliationState.INIT) {
                sources.update(task.tenantId(), current.supersede(), current.version());
                return false;
            }
            if (imports.activeForBatch(task.tenantId(), original.batchId()).isPresent())
                throw new CommonException(IMPORT_IN_PROGRESS);
            return true;
        });
        if (!pending) return;
        lease(task);
        var downloaded = payment.download(task.tenantId(), converter.toQuery(original));
        scope(original, downloaded);
        byte[] content = downloaded.content(), evidence = downloaded.evidence();
        var file = files.upload(task.tenantId(), original.callerServiceId(), "statement-" + original.id() + ".csv", "text/csv", content.length, new ByteArrayInputStream(content));
        var proof = files.upload(task.tenantId(), original.callerServiceId(), "statement-proof-" + original.id() + ".bin", "application/octet-stream", evidence.length, new ByteArrayInputStream(evidence));
        if (file == null || proof == null || file.size() != content.length || proof.size() != evidence.length
                || !file.sha256().equals(downloaded.contentSha256()) || !proof.sha256().equals(downloaded.evidenceSha256()))
            throw new CommonException(INPUT_INVALID);
        var receipt = converter.toReceipt(downloaded, file, proof);
        transactions.writeLocal(task.tenantId(), () -> {
            var batch = authorized(task.tenantId(), original, true);
            var current = sources.lock(task.tenantId(), original.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (current.state() != ReconciliationStatementState.INIT) {
                if (current.state() == ReconciliationStatementState.READY) linked(task.tenantId(), current);
                return false;
            }
            if (!original.equals(current)) throw new CommonException(SNAPSHOT_INVALID);
            lease(task);
            if (batch.batch().channel() != null || batch.batch().state() != ReconciliationState.INIT) {
                sources.update(task.tenantId(), current.supersede(), current.version());
                return false;
            }
            if (imports.activeForBatch(task.tenantId(), original.batchId()).isPresent())
                throw new CommonException(IMPORT_IN_PROGRESS);
            if (imports.find(task.tenantId(), original.id()).isPresent()) throw new CommonException(SNAPSHOT_INVALID);
            var ready = current.ready(receipt);
            files.attach(task.tenantId(), original.callerServiceId(), file, original.id());
            files.attach(task.tenantId(), original.callerServiceId(), proof, original.id());
            var transfer = transfers.submit(task.tenantId(), original.callerServiceId(), TransferKind.IMPORT,
                    ReconciliationImportExecutionService.HANDLER_KEY, file.fileId(), Map.of("importId", original.id()));
            if (transfer == null) throw new CommonException(SNAPSHOT_INVALID);
            var request = converter.toImport(ready, transfer.id());
            ReconciliationImportExecutionService.validateTask(transfer, request, TransferStatus.QUEUED);
            ready.verifyImport(request);
            sources.update(task.tenantId(), ready, current.version());
            imports.insert(task.tenantId(), request);
            official.verify(task.tenantId(), request);
            return true;
        });
    }

    private void linked(String tenant, ReconciliationStatementRequest source) {
        var request = imports.find(tenant, source.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        source.verifyImport(request);
        official.verify(tenant, request);
    }

    private StoredReconciliationBatch authorized(String tenant, ReconciliationStatementRequest source, boolean lock) {
        var batch = (lock ? batches.lock(tenant, source.batchId()) : batches.find(tenant, source.batchId())).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!source.callerServiceId().equals(batch.callerServiceId()) || !source.scope().equals(batch.batch().scope()))
            throw new CommonException(SCOPE_MISMATCH);
        accounts.authorize(tenant, source.callerServiceId(), batch.channelAccountCode(), source.scope());
        if (!source.statementAccount().equals(accounts.statementAccount(tenant, source.callerServiceId(), batch.channelAccountCode(), source.scope())))
            throw new CommonException(SCOPE_MISMATCH);
        return batch;
    }

    private void scope(ReconciliationStatementRequest original, PaymentStatementDTO value) {
        if (value == null || !original.bindingRevisionId().equals(value.bindingRevisionId()) || original.scope().channel() != value.channel()
                || !original.scope().accountScopeHash().equals(value.accountScopeHash()) || !original.scope().billDate().equals(value.billDate())
                || !original.scope().statementProfile().equals(value.statementProfile()) || value.content().length > original.maxBytes()
                || !"wechat-tradebill-grant-v1".equals(value.format()) || !"SHA1".equals(value.officialHashType()))
            throw new CommonException(SCOPE_MISMATCH);
    }

    private void lease(DelayedTask task) {
        if (task == null || task.tenantId() == null || !task.tenantId().equals(TenantUtil.getTenantId()) || !TYPE.equals(task.taskType())
                || task.id() == null || task.bizId() == null || task.status() != DelayedTaskStatus.RUNNING || task.attempts() < 1)
            throw new CommonException(INVALID_STATE);
        var actual = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var now = LocalDateTime.now(clock);
        if (!Objects.equals(actual.id(), task.id()) || !Objects.equals(actual.tenantId(), task.tenantId()) || !TYPE.equals(actual.taskType())
                || !Objects.equals(actual.bizId(), task.bizId()) || actual.status() != DelayedTaskStatus.RUNNING || actual.attempts() != task.attempts()
                || actual.maxAttempts() != task.maxAttempts() || actual.maxAttempts() < actual.attempts()
                || actual.executeAt() == null || !Objects.equals(actual.executeAt(), task.executeAt()) || actual.nextExecuteAt() == null
                || !Objects.equals(actual.nextExecuteAt(), task.nextExecuteAt()) || actual.nextExecuteAt().isAfter(now)
                || actual.leaseUntil() == null || !Objects.equals(actual.leaseUntil(), task.leaseUntil()) || !actual.leaseUntil().isAfter(now))
            throw new CommonException(INVALID_STATE);
    }
}
