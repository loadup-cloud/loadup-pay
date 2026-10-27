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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.payment.app.converter.PaymentCaptureConverter;
import io.github.loadup.pay.payment.app.port.PaymentCaptureReader;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.payment.domain.gateway.PaymentCaptureGateway;
import io.github.loadup.pay.payment.domain.model.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.Objects;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Service
public class PaymentCaptureExecutionService {
    public static final String TYPE = "payment.reconciliation.capture";
    private final PaymentCaptureGateway captures;
    private final PaymentCaptureReader reader;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentCaptureConverter converter;
    private final DelayedTaskTemplate tasks;
    private final Clock clock;
    private final int timeoutSeconds;
    private final String reconciliationService;
    public PaymentCaptureExecutionService(PaymentCaptureGateway captures, PaymentCaptureReader reader, PaymentAcceptanceTransactions transactions,
            PaymentCaptureConverter converter, DelayedTaskTemplate tasks, @Qualifier("paymentClock") Clock clock,
            @Value("${loadup.modules.payment.reconciliation.source-read-timeout-seconds:10}") int timeoutSeconds,
            @Value("${loadup.modules.payment.reconciliation-service-id:reconciliation}") String reconciliationService) {
        if (timeoutSeconds < 1 || timeoutSeconds > 60) throw new CommonException(INVALID_ARGUMENT);
        this.captures = captures; this.reader = reader; this.transactions = transactions; this.converter = converter; this.tasks = tasks; this.clock = clock; this.timeoutSeconds = timeoutSeconds; this.reconciliationService = io.github.loadup.commons.validation.DomainChecks.id(reconciliationService);
    }
    public void handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || task == null || task.tenantId() == null || !task.tenantId().equals(TenantUtil.getTenantId())
                || !TYPE.equals(task.taskType()) || task.status() != DelayedTaskStatus.RUNNING || task.bizId() == null || task.id() == null || task.attempts() < 1)
            throw new CommonException(INVALID_STATE);
        var actual = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!Objects.equals(actual.id(), task.id()) || !Objects.equals(actual.tenantId(), task.tenantId()) || !TYPE.equals(actual.taskType())
                || !Objects.equals(actual.bizId(), task.bizId()) || actual.status() != DelayedTaskStatus.RUNNING || actual.attempts() != task.attempts()
                || actual.maxAttempts() != task.maxAttempts() || actual.maxAttempts() < actual.attempts() || actual.executeAt() == null
                || !Objects.equals(actual.executeAt(), task.executeAt()) || actual.nextExecuteAt() == null || !Objects.equals(actual.nextExecuteAt(), task.nextExecuteAt())
                || actual.nextExecuteAt().isAfter(LocalDateTime.now(clock)) || actual.leaseUntil() == null || !actual.leaseUntil().isAfter(LocalDateTime.now(clock))) throw new CommonException(INVALID_STATE);
        var original = transactions.readSnapshot(task.tenantId(), () -> captures.find(task.tenantId(), task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)));
        if (!reconciliationService.equals(original.callerServiceId())) throw new CommonException(ACTION_FORBIDDEN);
        if (original.state() != PaymentCaptureState.INIT) return;
        if (!original.scope().systemZone().equals(clock.getZone().getId())) throw new CommonException(INVALID_STATE);
        var read = transactions.readSnapshot(task.tenantId(), timeoutSeconds, () -> reader.read(task.tenantId(), original.scope()));
        String hash = read.blocker() == null ? PaymentCaptureContentHash.compute(original.id(), original.scope().channel(), original.scope().accountScopeHash(),
                original.scope().billDate(), original.scope().statementProfile(), original.scope().systemZone(), converter.offset(read.startedAt(), original.scope().systemZone()),
                converter.offset(read.finishedAt(), original.scope().systemZone()), read.lines().stream().map(line -> converter.toDTO(line, original.scope().systemZone())).toList()) : null;
        transactions.writeLocal(task.tenantId(), () -> {
            var current = captures.lock(task.tenantId(), original.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (current.state() != PaymentCaptureState.INIT) return false;
            if (!current.equals(original)) throw new CommonException(SNAPSHOT_INVALID);
            captures.complete(task.tenantId(), original, original.complete(read, hash), read);
            return true;
        });
    }
}
