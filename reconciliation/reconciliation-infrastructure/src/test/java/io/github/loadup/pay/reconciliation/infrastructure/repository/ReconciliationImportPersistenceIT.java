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
package io.github.loadup.pay.reconciliation.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.dfs.DfsService;
import io.github.loadup.components.dfs.model.*;
import io.github.loadup.components.testcontainers.annotation.*;
import io.github.loadup.modules.file.client.facade.FileResourceFacade;
import io.github.loadup.modules.transfer.app.service.TransferTaskService;
import io.github.loadup.pay.reconciliation.app.converter.*;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.security.*;
import io.github.loadup.pay.reconciliation.app.service.*;
import io.github.loadup.pay.reconciliation.client.command.*;
import io.github.loadup.pay.reconciliation.client.dto.*;
import io.github.loadup.pay.reconciliation.domain.model.*;
import io.github.loadup.pay.reconciliation.infrastructure.client.FileResourceReconciliationSources;
import io.github.loadup.pay.reconciliation.infrastructure.converter.*;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.*;
import io.github.loadup.pay.reconciliation.infrastructure.statement.*;
import io.github.loadup.pay.reconciliation.infrastructure.task.ReconciliationImportTaskHandler;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real MySQL business/File/Transfer metadata; only external object bytes and trusted actor are supplied by the test. */
@SpringBootTest(classes = ReconciliationImportPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
class ReconciliationImportPersistenceIT {
    private final ReconciliationService batches;
    private final ReconciliationImportService imports;
    private final TransferTaskService scanner;
    private final FileResourceFacade files;
    private final FaultingImports persistence;
    private final JdbcTemplate jdbc;
    private final Actors actors;
    @Autowired ReconciliationImportPersistenceIT(ReconciliationService batches, ReconciliationImportService imports,
            TransferTaskService scanner, FileResourceFacade files, FaultingImports persistence, JdbcTemplate jdbc, Actors actors) {
        this.batches = batches; this.imports = imports; this.scanner = scanner; this.files = files; this.persistence = persistence; this.jdbc = jdbc; this.actors = actors;
    }
    @Test void registrationFailureRollsBackNativeTaskBothReferencesAndBusinessIntent() {
        var fixture = fixture(validFile());
        persistence.failInsertBatch.set(fixture.batchId());
        assertThrows(IllegalStateException.class, () -> actors.call(fixture.tenant(), () -> imports.submit(fixture.command())));
        assertEquals(0L, count("reconciliation_import", fixture.tenant())); assertEquals(0L, count("transfer_task", fixture.tenant()));
        assertEquals(0L, count("file_resource_reference", fixture.tenant()));
        assertEquals(1L, count("file_resource", fixture.tenant()));
        var accepted = actors.call(fixture.tenant(), () -> imports.submit(fixture.command()));
        assertEquals(2L, count("file_resource_reference", fixture.tenant()));
        assertTrue(scanner.executeQueuedTasks());
        var sealed = actors.call(fixture.tenant(), () -> imports.detail(new IdQuery(accepted.importId())));
        assertEquals("SEALED", sealed.state().name()); assertEquals("SUCCEEDED", sealed.taskStatus());
        assertEquals(1L, count("file_resource_reference", fixture.tenant()));
        TenantUtil.runWithTenant(fixture.tenant(), () -> assertThrows(CommonException.class, () -> files.requestDeletion(fixture.tenant(), fixture.file().fileId(), "ops", false)));
    }
    @Test void sealFailureRollsBackAllRowsBatchReferenceAndLogicalOutcomeThenOriginalTaskRetries() {
        var fixture = fixture(validFile());
        var accepted = actors.call(fixture.tenant(), () -> imports.submit(fixture.command()));
        persistence.failSealBatch.set(fixture.batchId());
        assertFalse(scanner.executeQueuedTasks());
        assertEquals(0L, count("reconciliation_input", fixture.tenant())); assertEquals(0L, count("reconciliation_entry", fixture.tenant()));
        var before = actors.call(fixture.tenant(), () -> imports.detail(new IdQuery(accepted.importId())));
        assertEquals("INIT", before.state().name()); assertEquals("FAILED", before.taskStatus());
        assertNull(actors.call(fixture.tenant(), () -> batches.detail(new IdQuery(fixture.batchId()))).channelInput());
        assertEquals(1L, count("file_resource_reference", fixture.tenant()));
        var queued = actors.call(fixture.tenant(), () -> imports.retry(new ReconciliationImportRetryCommand(accepted.importId(), accepted.taskId())));
        assertEquals(accepted.taskId(), queued.taskId()); assertTrue(scanner.executeQueuedTasks());
        var sealed = actors.call(fixture.tenant(), () -> imports.detail(new IdQuery(accepted.importId())));
        assertEquals("SEALED", sealed.state().name()); assertEquals("SUCCEEDED", sealed.taskStatus());
        assertEquals(1L, count("reconciliation_input", fixture.tenant())); assertEquals(1L, count("reconciliation_entry", fixture.tenant()));
        var replay = actors.call(fixture.tenant(), () -> imports.submit(fixture.command()));
        assertEquals(sealed, replay); assertEquals(1L, count("transfer_task", fixture.tenant()));
    }
    @Test void invalidTrailerIsPermanentRejectionWithNoSealedRowsAndNoEmptySuccess() {
        var fixture = fixture(validFile().replace("`1,`9.00,", "`2,`9.00,"));
        var accepted = actors.call(fixture.tenant(), () -> imports.submit(fixture.command()));
        assertFalse(scanner.executeQueuedTasks());
        var result = actors.call(fixture.tenant(), () -> imports.detail(new IdQuery(accepted.importId())));
        assertEquals("REJECTED", result.state().name()); assertEquals("FAILED", result.taskStatus()); assertNull(result.artifactId());
        assertEquals(0L, count("reconciliation_input", fixture.tenant())); assertEquals(0L, count("reconciliation_entry", fixture.tenant()));
        assertThrows(CommonException.class, () -> actors.call(fixture.tenant(), () -> imports.retry(new ReconciliationImportRetryCommand(result.importId(), result.taskId()))));
        assertNull(actors.call(fixture.tenant(), () -> batches.detail(new IdQuery(fixture.batchId()))).channelInput());
    }
    @Test void concurrentSameRequestHasOneTaskAndOneOriginalIntentAndOtherTenantCannotReadIt() throws Exception {
        var fixture = fixture(validFile()); var ready = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<ReconciliationImportDTO> call = () -> { ready.await(10, TimeUnit.SECONDS); return actors.call(fixture.tenant(), () -> imports.submit(fixture.command())); };
            var first = executor.submit(call); var second = executor.submit(call);
            var a = first.get(20, TimeUnit.SECONDS); var z = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.importId(), z.importId()); assertEquals(a.taskId(), z.taskId());
            assertEquals(1L, count("reconciliation_import", fixture.tenant())); assertEquals(1L, count("transfer_task", fixture.tenant()));
            assertThrows(CommonException.class, () -> actors.call("other-" + UUID.randomUUID(), () -> imports.detail(new IdQuery(a.importId()))));
        }
        assertTrue(scanner.executeQueuedTasks());
    }
    private Fixture fixture(String content) {
        String tenant = "tenant-" + UUID.randomUUID();
        var batch = actors.call(tenant, () -> batches.create(new ReconciliationBatchCreateCommand("batch-" + UUID.randomUUID(), "account", LocalDate.of(2026, 10, 26))));
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        var file = actors.call(tenant, () -> imports.upload("bill.csv", "text/csv", bytes.length, new ByteArrayInputStream(bytes)));
        return new Fixture(tenant, batch.batchId(), file, new ReconciliationChannelImportCommand(batch.batchId(), "request", file.fileId(), file.sha256()));
    }
    private long count(String table, String tenant) {
        // Table names are a closed test-only set; values always use bind parameters.
        if (!Set.of("reconciliation_import", "transfer_task", "file_resource_reference", "file_resource", "reconciliation_input", "reconciliation_entry").contains(table)) throw new AssertionError("Unexpected table");
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenant);
    }
    private String validFile() {
        var row = new String[27]; Arrays.fill(row, "");
        row[0] = "2026-10-26 12:00:00"; row[1] = "wx-app"; row[2] = "1234567890"; row[3] = "0"; row[5] = "transaction"; row[6] = "request";
        row[9] = "SUCCESS"; row[11] = "CNY"; row[12] = "9.00"; row[13] = "1.00"; row[14] = "0"; row[15] = "0";
        row[16] = "0.00"; row[17] = "0.00"; row[22] = "0.02"; row[24] = "10.00"; row[25] = "0.00";
        return WechatTradeStatementParser.HEADER + "\n" + Arrays.stream(row).map(value -> "`" + value).collect(java.util.stream.Collectors.joining(",")) + "\n"
                + WechatTradeStatementParser.TOTAL_HEADER + "\n`1,`9.00,`0.00,`0.00,`0.02,`10.00,`0.00\n";
    }
    private record Fixture(String tenant, String batchId, ReconciliationSourceFileDTO file, ReconciliationChannelImportCommand command) {}
    static final class Actors implements ReconciliationActorProvider {
        private final ThreadLocal<ReconciliationActor> current = new ThreadLocal<>();
        @Override public ReconciliationActor current() { return current.get(); }
        <T> T call(String tenant, Supplier<T> action) {
            current.set(new ReconciliationActor(tenant, "ops", Set.of("pay:reconciliation:batch:create", "pay:reconciliation:batch:read", "pay:reconciliation:import:upload",
                    "pay:reconciliation:import:submit", "pay:reconciliation:import:read", "pay:reconciliation:import:retry")));
            try { return action.get(); } finally { current.remove(); }
        }
    }
    static final class FaultingImports extends ReconciliationImportGatewayImpl {
        final AtomicReference<String> failInsertBatch = new AtomicReference<>(), failSealBatch = new AtomicReference<>();
        FaultingImports(ReconciliationImportDOMapper mapper, ReconciliationImportStorageConverter converter) { super(mapper, converter); }
        @Override public void insert(String tenant, ReconciliationImportRequest value) {
            super.insert(tenant, value);
            String target = failInsertBatch.get();
            if (value.batchId().equals(target) && failInsertBatch.compareAndSet(target, null)) throw new IllegalStateException("Injected after actual import registration");
        }
        @Override public void update(String tenant, ReconciliationImportRequest value, long expected) {
            super.update(tenant, value, expected);
            String target = failSealBatch.get();
            if (value.state() == ReconciliationImportState.SEALED && value.batchId().equals(target) && failSealBatch.compareAndSet(target, null)) throw new IllegalStateException("Injected after actual seal update");
        }
    }
    @SpringBootConfiguration @EnableAutoConfiguration
    @MapperScan(basePackageClasses = ReconciliationImportDOMapper.class)
    @ComponentScan(basePackageClasses = {ReconciliationImportStorageConverter.class, ReconciliationConverter.class})
    @Import({ReconciliationService.class, ReconciliationImportService.class, ReconciliationImportExecutionService.class, ReconciliationTransactions.class,
            ReconciliationBatchGatewayImpl.class, ReconciliationInputGatewayImpl.class, ReconciliationResultGatewayImpl.class,
            FileResourceReconciliationSources.class, ReconciliationStatementParserAdapter.class, WechatTradeStatementParser.class, ReconciliationImportTaskHandler.class})
    static class Application {
        @Bean Actors actors() { return new Actors(); }
        @Bean FaultingImports imports(ReconciliationImportDOMapper mapper, ReconciliationImportStorageConverter converter) { return new FaultingImports(mapper, converter); }
        @Bean ReconciliationMatcher matcher() { return new ReconciliationMatcher(); }
        @Bean("reconciliationClock") Clock clock() { return Clock.fixed(Instant.parse("2026-10-27T04:00:00Z"), ZoneId.systemDefault()); }
        @Bean ReconciliationAccountDirectory accounts() {
            return new ReconciliationAccountDirectory() {
                @Override public ReconciliationScope resolve(String tenant, String caller, String code, LocalDate date) { return new ReconciliationScope(Channel.WECHAT, "a".repeat(64), date, WechatTradeStatementParser.PROFILE); }
                @Override public void authorize(String tenant, String caller, String code, ReconciliationScope original) {}
                @Override public ReconciliationStatementAccount statementAccount(String tenant, String caller, String code, ReconciliationScope original) { return new ReconciliationStatementAccount("1234567890", null); }
            };
        }
        @Bean DfsService dfs() throws IOException {
            var service = mock(DfsService.class); var bytes = new ConcurrentHashMap<String, byte[]>(); var metadata = new ConcurrentHashMap<String, FileMetadata>();
            when(service.upload(any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                FileUploadRequest request = call.getArgument(0); var content = request.content().readAllBytes(); String id = UUID.randomUUID().toString();
                var object = new FileMetadata(id, request.filename(), content.length, request.contentType(), "test-object-store", id, Map.of(), Instant.now());
                bytes.put(id, content); metadata.put(id, object); return object;
            });
            when(service.download(anyString())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); String id = call.getArgument(0); var content = bytes.get(id);
                return new FileDownloadResponse(metadata.get(id), new ByteArrayInputStream(content), content.length);
            });
            when(service.delete(anyString())).thenAnswer(call -> { String id = call.getArgument(0); metadata.remove(id); return bytes.remove(id) != null; });
            return service;
        }
    }
}
