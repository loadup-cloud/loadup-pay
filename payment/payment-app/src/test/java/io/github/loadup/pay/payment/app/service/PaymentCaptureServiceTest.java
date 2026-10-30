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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.payment.app.converter.PaymentCaptureConverter;
import io.github.loadup.pay.payment.app.port.PaymentCaptureReader;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentCaptureCreateCommand;
import io.github.loadup.pay.payment.client.command.PaymentCaptureRetryCommand;
import io.github.loadup.pay.payment.domain.gateway.PaymentCaptureGateway;
import io.github.loadup.pay.payment.domain.model.PaymentCapture;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureRead;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureScope;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentCaptureServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentCaptureConverter.class.getPackageName());
    final PaymentCaptureConverter converter = context.getBean(PaymentCaptureConverter.class);
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final PaymentCaptureGateway captures = mock(PaymentCaptureGateway.class);
    final PaymentCaptureReader reader = mock(PaymentCaptureReader.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final Tx manager = new Tx();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final LocalDateTime at = LocalDateTime.of(2026, 10, 27, 12, 0);
    final Clock clock = Clock.fixed(at.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "reconciliation", Set.of(
            "pay:payment:reconciliation:capture:create", "pay:payment:reconciliation:capture:read", "pay:payment:reconciliation:capture:retry")));
    final AtomicReference<PaymentCapture> stored = new AtomicReference<>();
    final AtomicReference<DelayedTask> nativeTask = new AtomicReference<>();
    final PaymentCaptureCreateCommand command = new PaymentCaptureCreateCommand("request", Channel.WECHAT, "a".repeat(64), at.toLocalDate().minusDays(1), PaymentCaptureScope.WECHAT_ALL);
    final PaymentCaptureService service = new PaymentCaptureService(actor::get, captures, transactions, tasks, converter, () -> "capture", validation.getValidator(), clock, "reconciliation");
    final PaymentCaptureExecutionService execution = new PaymentCaptureExecutionService(captures, reader, transactions, converter, tasks, clock, 7, "reconciliation");

    @BeforeEach
    void setup() {
        when(captures.findRequest(anyString(), anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(captures.find("tenant", "capture")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(captures.lock("tenant", "capture")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.ofNullable(stored.get());
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            stored.set(call.getArgument(1));
            return null;
        }).when(captures).insert(eq("tenant"), any());
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertNotNull(stored.get());
            nativeTask.set(task(DelayedTaskStatus.PENDING, 0));
            return null;
        })
                .when(tasks).register(PaymentCaptureExecutionService.TYPE, "capture", at);
        when(tasks.find(PaymentCaptureExecutionService.TYPE, "capture")).thenAnswer(call -> Optional.ofNullable(nativeTask.get()));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            stored.set(call.getArgument(2));
            return null;
        })
                .when(captures).complete(eq("tenant"), any(), any(), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void originalRequestAndNativeRegistrationShareTheWriteBoundaryAndReplayNeverReregisters() {
        var first = service.create(command);
        assertEquals("native", first.nativeTaskId());
        assertEquals(first, service.create(command));
        verify(tasks, times(1)).register(PaymentCaptureExecutionService.TYPE, "capture", at);
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.create(new PaymentCaptureCreateCommand(
                "request", command.channel(), "b".repeat(64), command.billDate(), command.statementProfile()))).getResultCode());
        assertEquals(first, service.detail(new IdQuery("capture")));
        verifyNoInteractions(reader);
    }

    @Test
    void workerReadsOnceUnderBoundedReadonlyRrThenSealsOutsideTheReadTransaction() {
        service.create(command);
        nativeTask.set(task(DelayedTaskStatus.RUNNING, 1));
        var scope = stored.get().scope();
        when(reader.read("tenant", scope)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, manager.isolation);
            assertEquals(7, manager.timeout);
            assertEquals("tenant", TenantUtil.getTenantId());
            return new PaymentCaptureRead(scope, at, at, List.of(new PaymentLedgerLine(1, PaymentLedgerKind.PAYMENT, "txn", null, "payment-request",
                    Money.ofMinor(100, "CNY"), PaymentLedgerStatus.SUCCEEDED, command.billDate().atTime(10, 0), "payment", "receipt", 2L)), null, null);
        });
        TenantUtil.runWithTenant("tenant", () -> execution.handle(nativeTask.get()));
        assertEquals(PaymentCaptureState.SEALED, stored.get().state());
        assertNotNull(stored.get().contentHash());
        TenantUtil.runWithTenant("tenant", () -> execution.handle(nativeTask.get()));
        verify(reader, times(1)).read("tenant", scope);
        verify(captures, times(1)).complete(eq("tenant"), any(), any(), any());
    }

    @Test
    void blockedIsAFinalNonExportableResultAndStaleNativeMetadataDoesNotReadTheSource() {
        service.create(command);
        var running = task(DelayedTaskStatus.RUNNING, 1);
        nativeTask.set(task(DelayedTaskStatus.RUNNING, 2));
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () -> execution.handle(running))).getResultCode());
        verifyNoInteractions(reader);
        nativeTask.set(running);
        when(reader.read(eq("tenant"), any())).thenAnswer(call -> new PaymentCaptureRead(stored.get().scope(), at, at, List.of(), PaymentCaptureBlocker.REFUND_ACCEPTANCE_MISSING, "refund"));
        TenantUtil.runWithTenant("tenant", () -> execution.handle(running));
        assertEquals(PaymentCaptureState.BLOCKED, stored.get().state());
        assertNull(stored.get().contentHash());
        assertNull(stored.get().paymentCount());
    }

    @Test
    void failedTaskRetryKeepsItsOriginalIdentityAndCannotRefreshAFinalCapture() {
        service.create(command);
        nativeTask.set(task(DelayedTaskStatus.FAILED, 8));
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            nativeTask.set(task(DelayedTaskStatus.PENDING, 0));
            return null;
        })
                .when(tasks).retryFailed(PaymentCaptureExecutionService.TYPE, "capture");
        var original = stored.get();
        var result = service.retry(new PaymentCaptureRetryCommand("capture", "native"));
        assertEquals("PENDING", result.nativeStatus());
        assertEquals(original, stored.get());
        service.retry(new PaymentCaptureRetryCommand("capture", "native"));
        verify(tasks, times(1)).retryFailed(PaymentCaptureExecutionService.TYPE, "capture");
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.retry(new PaymentCaptureRetryCommand("capture", "different"))).getResultCode());
    }

    @Test
    void exactServiceIdentityAndPermissionsAreBothRequiredAndTodaysBillCannotBeCaptured() {
        actor.set(new PaymentActor("tenant", "other", actor.get().permissions()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.create(command)).getResultCode());
        actor.set(new PaymentActor("tenant", "reconciliation", Set.of()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.create(command)).getResultCode());
        actor.set(new PaymentActor("tenant", "reconciliation", Set.of("pay:payment:reconciliation:capture:create")));
        assertEquals(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.create(new PaymentCaptureCreateCommand("request", command.channel(), command.accountScopeHash(), at.toLocalDate(), command.statementProfile()))).getResultCode());
        verifyNoInteractions(captures, tasks, reader);
    }

    DelayedTask task(DelayedTaskStatus status, int attempts) {
        return new DelayedTask("native", PaymentCaptureExecutionService.TYPE, "capture", at, at, status, attempts, 8, status == DelayedTaskStatus.RUNNING ? at.plusMinutes(1) : null, null);
    }

    static final class Tx extends AbstractPlatformTransactionManager {
        int isolation, timeout;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
            isolation = definition.getIsolationLevel();
            timeout = definition.getTimeout();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
