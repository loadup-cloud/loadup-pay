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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.modules.transfer.client.dto.TransferTaskDTO;
import io.github.loadup.modules.transfer.client.enums.TransferKind;
import io.github.loadup.modules.transfer.client.enums.TransferStatus;
import io.github.loadup.modules.transfer.client.facade.TransferTaskFacade;
import io.github.loadup.pay.payment.client.dto.PaymentStatementDTO;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationStatementConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentStatements;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationStatementParser;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationStatementSubmitCommand;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSourceKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IMPORT_IN_PROGRESS;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.STATEMENT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit orchestration only. Actual rollback and native scanner semantics require separate PostgreSQL acceptance.
 */
class ReconciliationStatementServiceTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(ReconciliationStatementConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final ReconciliationStatementConverter converter = spring.getBean(ReconciliationStatementConverter.class);
    final ReconciliationAccountDirectory accounts = mock(ReconciliationAccountDirectory.class);
    final ReconciliationBatchGateway batches = mock(ReconciliationBatchGateway.class);
    final ReconciliationStatementGateway sources = mock(ReconciliationStatementGateway.class);
    final ReconciliationImportGateway imports = mock(ReconciliationImportGateway.class);
    final ReconciliationStatementParser parser = mock(ReconciliationStatementParser.class);
    final ReconciliationSourceFiles files = mock(ReconciliationSourceFiles.class);
    final ReconciliationPaymentStatements payment = mock(ReconciliationPaymentStatements.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final TransferTaskFacade transfers = mock(TransferTaskFacade.class);
    final ReconciliationTransactions tx = new ReconciliationTransactions(new Tx());
    final ReconciliationOfficialImports official = new ReconciliationOfficialImports(sources, files);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 12, 0);
    final Clock clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "wechat-trade-all-20260116");
    final ReconciliationStatementAccount account = new ReconciliationStatementAccount("1234567890", null);
    final AtomicReference<StoredReconciliationBatch> batch = new AtomicReference<>(new StoredReconciliationBatch(ReconciliationBatch.init("batch", scope), "ops", "batch-request", "b".repeat(64), "account"));
    final AtomicReference<ReconciliationStatementRequest> stored = new AtomicReference<>();
    final AtomicReference<ReconciliationImportRequest> imported = new AtomicReference<>();
    final AtomicReference<DelayedTask> nativeTask = new AtomicReference<>();
    final AtomicReference<ReconciliationActor> actor = new AtomicReference<>(new ReconciliationActor("tenant", "ops", Set.of("pay:reconciliation:statement:submit", "pay:reconciliation:statement:read", "pay:reconciliation:statement:retry")));
    final ReconciliationStatementService service = new ReconciliationStatementService(actor::get, accounts, batches, sources, imports, parser, tx, tasks, converter, official, () -> "source", validation.getValidator(), clock, 16777216);
    final ReconciliationStatementExecutionService execution = new ReconciliationStatementExecutionService(tasks, transfers, sources, batches, imports, accounts, payment, files, tx, converter, official, clock);
    final byte[] bytes = "synthetic-original-statement".getBytes(StandardCharsets.UTF_8), proof = "synthetic-private-proof".getBytes(StandardCharsets.UTF_8);
    final ReconciliationFileSnapshot file = new ReconciliationFileSnapshot("file", "storage", bytes.length, hash("SHA-256", bytes));
    final ReconciliationFileSnapshot evidence = new ReconciliationFileSnapshot("proof", "private-storage", proof.length, hash("SHA-256", proof));

    static String hash(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static void assertWrite() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @BeforeEach
    void setup() {
        when(batches.find("tenant", "batch")).thenAnswer(call -> Optional.of(batch.get()));
        when(batches.lock("tenant", "batch")).thenAnswer(call -> {
            assertWrite();
            return Optional.of(batch.get());
        });
        when(accounts.statementAccount("tenant", "ops", "account", scope)).thenReturn(account);
        when(accounts.statementBindingRevision("tenant", "ops", "account", scope)).thenReturn("revision");
        when(parser.supports(scope)).thenReturn(true);
        when(sources.findRequest(anyString(), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(sources.find("tenant", "source")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(sources.lock("tenant", "source")).thenAnswer(call -> {
            assertWrite();
            return Optional.ofNullable(stored.get());
        });
        when(sources.activeForBatch(anyString(), anyString())).thenReturn(Optional.empty());
        when(imports.activeForBatch(anyString(), anyString())).thenReturn(Optional.empty());
        when(imports.find("tenant", "source")).thenAnswer(call -> Optional.ofNullable(imported.get()));
        doAnswer(call -> {
            assertWrite();
            stored.set(call.getArgument(1));
            return null;
        }).when(sources).insert(anyString(), any());
        doAnswer(call -> {
            assertWrite();
            stored.set(call.getArgument(1));
            return null;
        }).when(sources).update(anyString(), any(), anyLong());
        doAnswer(call -> {
            assertWrite();
            imported.set(call.getArgument(1));
            return null;
        }).when(imports).insert(anyString(), any());
        doAnswer(call -> {
            assertWrite();
            nativeTask.set(task(DelayedTaskStatus.PENDING));
            return null;
        }).when(tasks).register(ReconciliationStatementExecutionService.TYPE, "source", now);
        when(tasks.find(ReconciliationStatementExecutionService.TYPE, "source")).thenAnswer(call -> Optional.ofNullable(nativeTask.get()));
        when(payment.download(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertNotNull(stored.get());
            assertEquals(ReconciliationStatementState.INIT, stored.get().state());
            assertEquals("revision", ((io.github.loadup.pay.payment.client.query.PaymentStatementQuery) call.getArgument(1)).bindingRevisionId());
            return downloaded("revision");
        });
        when(files.upload(anyString(), anyString(), anyString(), anyString(), anyLong(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return ((String) call.getArgument(2)).endsWith(".csv") ? file : evidence;
        });
        when(transfers.submit(eq("tenant"), eq("ops"), eq(TransferKind.IMPORT), eq(ReconciliationImportExecutionService.HANDLER_KEY), eq("file"), eq(Map.of("importId", "source"))))
                .thenAnswer(call -> {
                    assertWrite();
                    return transfer();
                });
    }

    @AfterEach
    void close() {
        spring.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void originalIntentPrecedesNetworkAndFirstReadyCommitNeverDownloadsAgainAfterLostNativeAck() {
        var accepted = service.submit(new ReconciliationStatementSubmitCommand("batch", "request"));
        assertEquals("INIT", accepted.state().name());
        assertNull(accepted.importId());
        verifyNoInteractions(payment, files, transfers);
        assertEquals(accepted, service.submit(new ReconciliationStatementSubmitCommand("batch", "request")));
        nativeTask.set(task(DelayedTaskStatus.RUNNING));
        handle();
        assertEquals(ReconciliationStatementState.READY, stored.get().state());
        assertEquals("revision", stored.get().bindingRevisionId());
        assertEquals(ReconciliationSourceKind.OFFICIAL_DOWNLOAD, imported.get().sourceKind());
        assertEquals("official:source", imported.get().requestKey());
        assertEquals(ReconciliationImportState.INIT, imported.get().state());
        assertEquals("source", service.detail(new IdQuery("source")).importId());
        verify(files).attach("tenant", "ops", file, "source");
        verify(files).attach("tenant", "ops", evidence, "source");
        handle();
        verify(payment, times(1)).download(eq("tenant"), any());
        verify(transfers, times(1)).submit(anyString(), anyString(), any(), anyString(), anyString(), anyMap());
        nativeTask.set(task(DelayedTaskStatus.FAILED));
        doAnswer(call -> {
            nativeTask.set(task(DelayedTaskStatus.PENDING));
            return null;
        }).when(tasks).retryFailed(ReconciliationStatementExecutionService.TYPE, "source");
        service.retry(new ReconciliationStatementRetryCommand("source", "download-task"));
        assertEquals(ReconciliationStatementState.READY, stored.get().state());
        verify(payment, times(1)).download(eq("tenant"), any());
    }

    @Test
    void unavailableSourceAndWrongBindingNeverRegisterFilesOrTransfer() {
        service.submit(new ReconciliationStatementSubmitCommand("batch", "request"));
        nativeTask.set(task(DelayedTaskStatus.RUNNING));
        doThrow(new CommonException(STATEMENT_NOT_FOUND)).when(payment).download(anyString(), any());
        assertEquals(STATEMENT_NOT_FOUND, assertThrows(CommonException.class, this::handle).getResultCode());
        assertEquals(ReconciliationStatementState.INIT, stored.get().state());
        assertNull(imported.get());
        verifyNoInteractions(files, transfers);
        doReturn(downloaded("different")).when(payment).download(anyString(), any());
        assertEquals(SCOPE_MISMATCH, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(files, transfers);
    }

    @Test
    void anActiveManualImportStopsDownloadAndAnExpiredLeaseCannotCommit() {
        service.submit(new ReconciliationStatementSubmitCommand("batch", "request"));
        nativeTask.set(task(DelayedTaskStatus.RUNNING));
        when(imports.activeForBatch("tenant", "batch")).thenReturn(Optional.of(ReconciliationImportRequest.init("manual", "batch", "ops", "manual", "b".repeat(64), scope, account, file, "manual-task")));
        assertEquals(IMPORT_IN_PROGRESS, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(payment, files, transfers);
        when(imports.activeForBatch("tenant", "batch")).thenReturn(Optional.empty());
        var running = nativeTask.get();
        doAnswer(call -> {
            nativeTask.set(new DelayedTask(running.id(), running.tenantId(), running.taskType(), running.bizId(), running.executeAt(), running.nextExecuteAt(), DelayedTaskStatus.RUNNING, 2, 5, now.plusMinutes(2), null));
            return downloaded("revision");
        }).when(payment).download(anyString(), any());
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, this::handle).getResultCode());
        assertEquals(ReconciliationStatementState.INIT, stored.get().state());
        assertNull(imported.get());
        verifyNoInteractions(transfers);
    }

    @Test
    void retryRejectsOtherTaskIdentityAndPermissionIsRequiredBeforeStorage() {
        actor.set(new ReconciliationActor("tenant", "ops", Set.of()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.submit(new ReconciliationStatementSubmitCommand("batch", "request"))).getResultCode());
        verifyNoInteractions(batches, sources, tasks);
        actor.set(new ReconciliationActor("tenant", "ops", Set.of("pay:reconciliation:statement:submit", "pay:reconciliation:statement:retry")));
        service.submit(new ReconciliationStatementSubmitCommand("batch", "request"));
        nativeTask.set(task(DelayedTaskStatus.FAILED));
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.retry(new ReconciliationStatementRetryCommand("source", "other-task"))).getResultCode());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    void handle() {
        TenantUtil.runWithTenant("tenant", () -> execution.handle(nativeTask.get()));
    }

    PaymentStatementDTO downloaded(String revision) {
        var at = OffsetDateTime.parse("2026-10-28T10:00:00.123456+08:00");
        return new PaymentStatementDTO(revision, scope.channel(), scope.accountScopeHash(), scope.billDate(), scope.statementProfile(), "wechat-tradebill-grant-v1", "e".repeat(64), evidence.sha256(), "SHA1", hash("SHA-1", bytes), file.sha256(), at, at.plusSeconds(1), proof, bytes);
    }

    DelayedTask task(DelayedTaskStatus status) {
        return new DelayedTask("download-task", "tenant", ReconciliationStatementExecutionService.TYPE, "source", now, now, status, status == DelayedTaskStatus.RUNNING || status == DelayedTaskStatus.FAILED ? 1 : 0, 5, status == DelayedTaskStatus.RUNNING ? now.plusMinutes(1) : null, null);
    }

    TransferTaskDTO transfer() {
        return new TransferTaskDTO("transfer-task", "tenant", "ops", TransferKind.IMPORT, ReconciliationImportExecutionService.HANDLER_KEY, "file", null, TransferStatus.QUEUED, 0L, 0L, null, now, null, null);
    }

    static class Tx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
