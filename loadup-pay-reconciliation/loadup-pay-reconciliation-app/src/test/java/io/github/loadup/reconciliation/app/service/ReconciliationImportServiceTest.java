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
package io.github.loadup.reconciliation.app.service;

import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.IMPORT_IN_PROGRESS;
import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.modules.transfer.client.dto.TransferTaskDTO;
import io.github.loadup.modules.transfer.client.enums.TransferKind;
import io.github.loadup.modules.transfer.client.enums.TransferStatus;
import io.github.loadup.modules.transfer.client.facade.TransferTaskFacade;
import io.github.loadup.modules.transfer.client.spi.TransferContext;
import io.github.loadup.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.reconciliation.app.converter.ReconciliationImportConverter;
import io.github.loadup.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.reconciliation.app.port.ReconciliationStatementParser;
import io.github.loadup.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.reconciliation.client.command.ReconciliationChannelImportCommand;
import io.github.loadup.reconciliation.client.command.ReconciliationImportRetryCommand;
import io.github.loadup.reconciliation.client.enums.ReconciliationImportState;
import io.github.loadup.reconciliation.client.enums.ReconciliationSide;
import io.github.loadup.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.reconciliation.domain.model.StoredReconciliationBatch;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ReconciliationImportServiceTest {
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(ReconciliationConverter.class.getPackageName());
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final ReconciliationImportGateway imports = mock(ReconciliationImportGateway.class);
    private final ReconciliationBatchGateway batches = mock(ReconciliationBatchGateway.class);
    private final ReconciliationInputGateway inputs = mock(ReconciliationInputGateway.class);
    private final ReconciliationAccountDirectory accounts = mock(ReconciliationAccountDirectory.class);
    private final ReconciliationSourceFiles files = mock(ReconciliationSourceFiles.class);
    private final ReconciliationStatementParser parser = mock(ReconciliationStatementParser.class);
    private final TransferTaskFacade tasks = mock(TransferTaskFacade.class);
    private final ReconciliationTransactions tx = new ReconciliationTransactions(new Tx());
    private final ReconciliationScope scope =
            new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "parser");
    private final ReconciliationStatementAccount account = new ReconciliationStatementAccount("1234567890", null);
    private final ReconciliationFileSnapshot source =
            new ReconciliationFileSnapshot("file", "storage", 100, "b".repeat(64));
    private final AtomicReference<ReconciliationImportRequest> stored = new AtomicReference<>();
    private final AtomicReference<StoredReconciliationBatch> batch =
            new AtomicReference<>(new StoredReconciliationBatch(
                    ReconciliationBatch.init("batch", scope), "ops", "batch-request", "c".repeat(64), "account"));
    private final ReconciliationActor actor = new ReconciliationActor(
            "tenant",
            "ops",
            Set.of(
                    "pay:reconciliation:import:upload",
                    "pay:reconciliation:import:submit",
                    "pay:reconciliation:import:read",
                    "pay:reconciliation:import:retry"));
    private final ReconciliationImportService service = new ReconciliationImportService(
            () -> actor,
            accounts,
            batches,
            imports,
            files,
            parser,
            tx,
            tasks,
            context.getBean(ReconciliationImportConverter.class),
            () -> "import",
            validation.getValidator(),
            new ReconciliationOfficialImports(mock(ReconciliationStatementGateway.class), files));

    @BeforeEach
    void setup() {
        when(batches.find("batch")).thenAnswer(call -> Optional.of(batch.get()));
        when(batches.lock("batch")).thenAnswer(call -> Optional.of(batch.get()));
        when(accounts.statementAccount("ops", "account", scope)).thenReturn(account);
        when(parser.supports(scope)).thenReturn(true);
        when(files.inspect("ops", "file")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return source;
        });
        when(imports.findRequest("ops", "request")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(imports.find("import")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(imports.lock("import")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(imports.activeForBatch("batch")).thenReturn(Optional.empty());
        doAnswer(call -> {
                    stored.set(call.getArgument(0));
                    return null;
                })
                .when(imports)
                .insert(any());
        doAnswer(call -> {
                    stored.set(call.getArgument(0));
                    return null;
                })
                .when(imports)
                .update(any(), anyLong());
        doAnswer(call -> {
                    batch.set(call.getArgument(0));
                    return null;
                })
                .when(batches)
                .update(any(), anyLong());
        when(tasks.submit(
                        eq("ops"),
                        eq(TransferKind.IMPORT),
                        eq(ReconciliationImportExecutionService.HANDLER_KEY),
                        eq("file"),
                        eq(Map.of("importId", "import"))))
                .thenAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                    return task(TransferStatus.QUEUED);
                });
        when(tasks.get("task", "ops", false)).thenReturn(task(TransferStatus.QUEUED));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void registrationPinsAndEnqueuesInTheWriteTransactionAndFullReplayDoesNoFileIo() {
        var first = service.submit(command("file"));
        assertEquals("MANUAL_UPLOAD", first.sourceKind().name());
        assertEquals("INIT", first.state().name());
        assertNull(first.artifactId());
        verify(files).attach("ops", source, "import");
        verify(imports).insert(any());
        clearInvocations(files, tasks, imports);
        assertEquals(first, service.submit(command("file")));
        verifyNoInteractions(files);
        verify(tasks, never()).submit(anyString(), any(), anyString(), anyString(), anyMap());
        assertEquals(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.submit(command("different-file")))
                        .getResultCode());
    }

    @Test
    void changedExpectedHashAndAnotherActiveImportCannotEnqueueANewTask() {
        assertEquals(
                INPUT_INVALID,
                assertThrows(
                                CommonException.class,
                                () -> service.submit(new ReconciliationChannelImportCommand(
                                        "batch", "request", "file", "d".repeat(64))))
                        .getResultCode());
        verify(tasks, never()).submit(anyString(), any(), anyString(), anyString(), anyMap());
        var another = ReconciliationImportRequest.init(
                "other-import", "batch", "ops", "other-request", "e".repeat(64), scope, account, source, "other-task");
        when(imports.activeForBatch("batch")).thenReturn(Optional.of(another));
        assertEquals(
                IMPORT_IN_PROGRESS,
                assertThrows(CommonException.class, () -> service.submit(command("file")))
                        .getResultCode());
        verify(files, never()).attach(anyString(), any(), anyString());
    }

    @Test
    void nativeEnqueueFailureCannotInsertALogicalImport() {
        when(tasks.submit(anyString(), any(), anyString(), anyString(), anyMap()))
                .thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> service.submit(command("file")));
        assertNull(stored.get());
        verify(imports, never()).insert(any());
        // Mockito proves call ordering only; File/Transfer/intent SQL rollback still requires the real-DB IT.
    }

    @Test
    void parsedRowsSealTogetherThenDuplicateHandlerInvocationDoesNotParseAgain() {
        service.submit(command("file"));
        when(tasks.get("task", "ops", false)).thenReturn(task(TransferStatus.RUNNING));
        var data = data("b".repeat(64));
        when(parser.parse(any(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return data;
        });
        when(inputs.sealedManifest("import")).thenReturn(Optional.of(data.manifest()));
        var execution = execution();
        TenantUtil.runWithTenant("tenant", () -> execution.handle(nativeContext(), InputStream.nullInputStream()));
        assertEquals(ReconciliationImportState.SEALED, stored.get().state());
        assertEquals(data.manifest(), batch.get().batch().channel());
        TenantUtil.runWithTenant("tenant", () -> execution.handle(nativeContext(), InputStream.nullInputStream()));
        verify(parser, times(1)).parse(any(), any());
        verify(inputs, times(1)).insertSealed("file", data);
        verify(imports, times(1)).update(any(), eq(0L));
    }

    @Test
    void changedBytesRejectWholeInputWhileTransientParserFailureLeavesItPending() {
        service.submit(command("file"));
        when(tasks.get("task", "ops", false)).thenReturn(task(TransferStatus.RUNNING));
        when(parser.parse(any(), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        var execution = execution();
        assertThrows(
                CommonException.class,
                () -> TenantUtil.runWithTenant(
                        "tenant", () -> execution.handle(nativeContext(), InputStream.nullInputStream())));
        assertEquals(ReconciliationImportState.INIT, stored.get().state());
        doReturn(data("d".repeat(64))).when(parser).parse(any(), any());
        assertEquals(
                INPUT_INVALID,
                assertThrows(
                                CommonException.class,
                                () -> TenantUtil.runWithTenant(
                                        "tenant",
                                        () -> execution.handle(nativeContext(), InputStream.nullInputStream())))
                        .getResultCode());
        assertEquals(ReconciliationImportState.REJECTED, stored.get().state());
        assertNull(batch.get().batch().channel());
        verify(parser, times(2)).parse(any(), any());
        verify(inputs, never()).insertSealed(anyString(), any());
    }

    @Test
    void arbitraryOptionsOrWrongNativeBindingCannotReachTheParserAndRunningTaskIsNeverReclaimed() {
        service.submit(command("file"));
        when(tasks.get("task", "ops", false)).thenReturn(task(TransferStatus.RUNNING));
        var execution = execution();
        var forged = new TransferContext("other-task", "ops", Map.of("importId", "import"), (done, total) -> {});
        assertThrows(
                CommonException.class,
                () -> TenantUtil.runWithTenant(
                        "tenant", () -> execution.handle(forged, InputStream.nullInputStream())));
        var extra = new TransferContext(
                "task", "ops", Map.of("importId", "import", "complete", "true"), (done, total) -> {});
        assertThrows(
                CommonException.class,
                () -> TenantUtil.runWithTenant("tenant", () -> execution.handle(extra, InputStream.nullInputStream())));
        assertThrows(
                CommonException.class, () -> service.retry(new ReconciliationImportRetryCommand("import", "task")));
        verify(parser, never()).parse(any(), any());
        verify(tasks, never()).retry(anyString(), anyString(), anyBoolean());
        when(tasks.get("task", "ops", false)).thenReturn(task(TransferStatus.FAILED));
        when(tasks.retry("task", "ops", false)).thenReturn(task(TransferStatus.QUEUED));
        assertEquals(
                "QUEUED",
                service.retry(new ReconciliationImportRetryCommand("import", "task"))
                        .taskStatus());
        verify(files).verify("ops", source, "import");
    }

    private ReconciliationImportExecutionService execution() {
        @SuppressWarnings("unchecked")
        ObjectProvider<TransferTaskFacade> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(tasks);
        return new ReconciliationImportExecutionService(
                provider,
                imports,
                batches,
                inputs,
                files,
                accounts,
                parser,
                tx,
                context.getBean(ReconciliationConverter.class),
                new ReconciliationOfficialImports(mock(ReconciliationStatementGateway.class), files));
    }

    private ReconciliationDataset data(String rawHash) {
        var rows = List.<ReconciliationLine>of();
        return new ReconciliationDataset(
                new ReconciliationManifest(
                        ReconciliationSide.CHANNEL,
                        scope,
                        "import",
                        rawHash,
                        ReconciliationDataset.normalizedHash(scope, ReconciliationSide.CHANNEL, "parser", rows),
                        "import",
                        "parser",
                        ReconciliationTotals.count(rows)),
                rows);
    }

    private ReconciliationChannelImportCommand command(String file) {
        return new ReconciliationChannelImportCommand("batch", "request", file, "b".repeat(64));
    }

    private TransferContext nativeContext() {
        return new TransferContext("task", "ops", Map.of("importId", "import"), (done, total) -> {});
    }

    private TransferTaskDTO task(TransferStatus state) {
        return new TransferTaskDTO(
                "task",
                "ops",
                TransferKind.IMPORT,
                ReconciliationImportExecutionService.HANDLER_KEY,
                "file",
                null,
                state,
                0L,
                0L,
                null,
                LocalDateTime.of(2026, 10, 27, 12, 0),
                null,
                null);
    }

    static final class Tx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
