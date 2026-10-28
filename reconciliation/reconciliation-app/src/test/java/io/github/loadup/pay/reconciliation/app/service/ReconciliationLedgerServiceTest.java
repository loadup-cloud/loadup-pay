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
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCaptureDTO;
import io.github.loadup.pay.payment.client.dto.PaymentLedgerLineDTO;
import io.github.loadup.pay.payment.client.enums.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.client.enums.PaymentCaptureState;
import io.github.loadup.pay.payment.client.enums.PaymentLedgerKind;
import io.github.loadup.pay.payment.client.enums.PaymentLedgerStatus;
import io.github.loadup.pay.payment.client.query.PaymentCaptureLinesQuery;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationLedgerConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentCaptures;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationLedgerImportCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationLedgerPrepareCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationLedgerRetryCommand;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
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

import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_NOT_SEALED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SCOPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReconciliationLedgerServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ReconciliationConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final ReconciliationBatchGateway batches = mock(ReconciliationBatchGateway.class);
    final ReconciliationInputGateway inputs = mock(ReconciliationInputGateway.class);
    final ReconciliationAccountDirectory accounts = mock(ReconciliationAccountDirectory.class);
    final ReconciliationPaymentCaptures source = mock(ReconciliationPaymentCaptures.class);
    final ReconciliationSourceFiles files = mock(ReconciliationSourceFiles.class);
    final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "wechat-trade-all-20260116");
    final AtomicReference<StoredReconciliationBatch> stored = new AtomicReference<>(new StoredReconciliationBatch(
            ReconciliationBatch.init("batch", scope), "ops", "batch-request", "b".repeat(64), "account"));
    final AtomicReference<ReconciliationDataset> sealed = new AtomicReference<>();
    final ReconciliationActor actor = new ReconciliationActor("tenant", "ops", Set.of("pay:reconciliation:ledger:prepare", "pay:reconciliation:ledger:import", "pay:reconciliation:ledger:retry"));
    final ReconciliationTransactions transactions = new ReconciliationTransactions(new Tx());
    final ReconciliationLedgerService service = new ReconciliationLedgerService(() -> actor, accounts, batches, inputs, source, files, transactions,
            context.getBean(ReconciliationLedgerConverter.class), context.getBean(ReconciliationConverter.class), () -> "artifact", validation.getValidator(), 10000, 16777216, 60);
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-27T10:00:00.123456+08:00");
    final PaymentLedgerLineDTO row = new PaymentLedgerLineDTO(1, PaymentLedgerKind.PAYMENT, "txn", null, "request", 100, "CNY", PaymentLedgerStatus.SUCCEEDED, at.minusDays(1), "payment", "receipt", 2L);
    final PaymentCaptureDTO capture = capture(PaymentCaptureState.SEALED, scope.accountScopeHash());

    @BeforeEach
    void setup() {
        when(batches.find("tenant", "batch")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(stored.get());
        });
        when(batches.lock("tenant", "batch")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(stored.get());
        });
        doAnswer(call -> {
            stored.set(call.getArgument(1));
            return null;
        }).when(batches).update(eq("tenant"), any(), anyLong());
        when(source.detail("tenant", "capture")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return capture;
        });
        when(source.lines(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            PaymentCaptureLinesQuery query = call.getArgument(1);
            return PageDTO.of(query.page() == 1 ? List.of(row) : List.of(), 1L, query.page(), 100);
        });
        when(files.upload(eq("tenant"), eq("ops"), anyString(), eq("application/octet-stream"), anyLong(), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            byte[] bytes = ((InputStream) call.getArgument(5)).readAllBytes();
            assertArrayEquals(PaymentCaptureContentHash.archive(capture, List.of(row), 16777216), bytes);
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(capture.contentHash(), hash);
            return new ReconciliationFileSnapshot("file", "private-object", bytes.length, hash);
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            sealed.set(call.getArgument(2));
            return null;
        })
                .when(inputs).insertSealed(eq("tenant"), eq("file"), any());
        when(inputs.sealedManifest(eq("tenant"), anyString())).thenAnswer(call -> Optional.ofNullable(sealed.get()).map(ReconciliationDataset::manifest));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void entireSourceAndEmptySentinelAreValidatedBeforeAtomicFileReferenceInputAndBatchWrites() {
        var result = service.importSealed(new ReconciliationLedgerImportCommand("batch", "capture"));
        assertNotNull(result.ledgerInput());
        assertEquals("capture", result.ledgerInput().sourceCaptureId());
        assertEquals(ReconciliationSide.LEDGER, sealed.get().manifest().side());
        assertEquals(100L, sealed.get().manifest().totals().paymentAmountMinor());
        var order = inOrder(files, inputs, batches);
        order.verify(files).attach(eq("tenant"), eq("ops"), any(), eq("artifact"));
        order.verify(files).verify(eq("tenant"), eq("ops"), any(), eq("artifact"));
        order.verify(inputs).insertSealed(eq("tenant"), eq("file"), any());
        order.verify(batches).update(eq("tenant"), any(), eq(0L));
        verify(source).lines("tenant", new PaymentCaptureLinesQuery("capture", 2, 100));
        clearInvocations(source, files);
        assertEquals(result, service.importSealed(new ReconciliationLedgerImportCommand("batch", "capture")));
        verifyNoInteractions(source, files);
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.importSealed(new ReconciliationLedgerImportCommand("batch", "other"))).getResultCode());
    }

    @Test
    void partialPageChangedTotalsAndWrongAccountNeverCreateAnInput() {
        when(source.lines("tenant", new PaymentCaptureLinesQuery("capture", 1, 100))).thenReturn(PageDTO.of(List.of(), 1L, 1, 100));
        assertEquals(INPUT_INVALID, assertThrows(CommonException.class, () -> service.importSealed(new ReconciliationLedgerImportCommand("batch", "capture"))).getResultCode());
        verifyNoInteractions(files, inputs);
        verify(batches, never()).update(anyString(), any(), anyLong());
        when(source.detail("tenant", "capture")).thenReturn(capture(PaymentCaptureState.SEALED, "c".repeat(64)));
        assertEquals(SCOPE_MISMATCH, assertThrows(CommonException.class, () -> service.importSealed(new ReconciliationLedgerImportCommand("batch", "capture"))).getResultCode());
    }

    @Test
    void initAndBlockedCannotBeMistakenForAnEmptySuccessfulSource() {
        for (var state : List.of(PaymentCaptureState.INIT, PaymentCaptureState.BLOCKED)) {
            when(source.detail("tenant", "capture")).thenReturn(capture(state, scope.accountScopeHash()));
            assertEquals(INPUT_NOT_SEALED, assertThrows(CommonException.class, () -> service.importSealed(new ReconciliationLedgerImportCommand("batch", "capture"))).getResultCode());
        }
        verify(source, never()).lines(anyString(), any());
        verifyNoInteractions(files, inputs);
    }

    @Test
    void accountRevokedDuringFetchIsRecheckedBeforeAnyLocalReferenceOrInputWrite() {
        when(source.detail("tenant", "capture")).thenAnswer(call -> {
            doThrow(new CommonException(ACTION_FORBIDDEN)).when(accounts).authorize("tenant", "ops", "account", scope);
            return capture;
        });
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.importSealed(new ReconciliationLedgerImportCommand("batch", "capture"))).getResultCode());
        verify(files, never()).attach(anyString(), anyString(), any(), anyString());
        verify(inputs, never()).insertSealed(anyString(), anyString(), any());
        assertNull(stored.get().batch().ledger());
    }

    @Test
    void prepareUsesOnlyOriginalBatchScopeAndRetryRequiresTheExactOriginalTask() {
        when(source.create(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand command = call.getArgument(1);
            assertEquals(scope.channel(), command.channel());
            assertEquals(scope.accountScopeHash(), command.accountScopeHash());
            assertEquals(scope.billDate(), command.billDate());
            assertTrue(command.requestKey().matches("[0-9a-f]{64}"));
            return capture;
        });
        assertEquals("capture", service.prepare(new ReconciliationLedgerPrepareCommand("batch", "request")).captureId());
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.retry(new ReconciliationLedgerRetryCommand("batch", "capture", "other-task"))).getResultCode());
        verify(source, never()).retry(anyString(), any());
    }

    private PaymentCaptureDTO capture(PaymentCaptureState state, String hash) {
        boolean initial = state == PaymentCaptureState.INIT, complete = state == PaymentCaptureState.SEALED;
        String content = complete ? PaymentCaptureContentHash.compute("capture", scope.channel(), hash, scope.billDate(), scope.statementProfile(), "Asia/Shanghai", at, at, List.of(row)) : null;
        return new PaymentCaptureDTO("capture", scope.channel(), hash, scope.billDate(), scope.statementProfile(), "Asia/Shanghai", state, initial ? null : at, initial ? null : at,
                complete ? 1L : null, complete ? 100L : null, complete ? 0L : null, complete ? 0L : null, content,
                state == PaymentCaptureState.BLOCKED ? PaymentCaptureBlocker.UNRESOLVED_COLLECTION : null, state == PaymentCaptureState.BLOCKED ? "payment" : null,
                initial ? 0L : 1L, "native", "SUCCEEDED", 1, 5);
    }

    static final class Tx extends AbstractPlatformTransactionManager {
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
