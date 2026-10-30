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
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationResultGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationMatcher;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Read immutable local inputs, compare outside transactions, then commit differences and completion together.
 */
@Service
public class ReconciliationExecutionService {
    public static final String TYPE = "pay.reconciliation.compare";
    private final DelayedTaskTemplate tasks;
    private final ReconciliationBatchGateway batches;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationResultGateway results;
    private final ReconciliationTransactions transactions;
    private final ReconciliationConverter converter;
    private final ReconciliationMatcher matcher;
    private final Clock clock;

    public ReconciliationExecutionService(DelayedTaskTemplate tasks, ReconciliationBatchGateway batches, ReconciliationInputGateway inputs,
                                          ReconciliationResultGateway results, ReconciliationTransactions transactions, ReconciliationConverter converter,
                                          ReconciliationMatcher matcher, Clock clock) {
        this.tasks = tasks;
        this.batches = batches;
        this.inputs = inputs;
        this.results = results;
        this.transactions = transactions;
        this.converter = converter;
        this.matcher = matcher;
        this.clock = clock;
    }

    public void handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (task == null || !TYPE.equals(task.taskType())
                || task.status() != DelayedTaskStatus.RUNNING || task.attempts() <= 0 || task.maxAttempts() < task.attempts()) {
            throw new CommonException(INVALID_STATE);
        }
        if (task.id() == null || task.id().isBlank() || task.bizId() == null || task.bizId().isBlank()
                || task.leaseUntil() == null || task.nextExecuteAt() == null) {
            throw new CommonException(INVALID_STATE);
        }
        var registered = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var now = LocalDateTime.now(clock);
        if (!Objects.equals(task.id(), registered.id())
                || !TYPE.equals(registered.taskType()) || !Objects.equals(task.bizId(), registered.bizId())
                || !Objects.equals(task.executeAt(), registered.executeAt()) || task.executeAt() == null
                || !Objects.equals(task.nextExecuteAt(), registered.nextExecuteAt())
                || task.attempts() != registered.attempts() || task.maxAttempts() != registered.maxAttempts()
                || registered.status() != DelayedTaskStatus.RUNNING || registered.leaseUntil() == null || !registered.leaseUntil().isAfter(now)
                || registered.nextExecuteAt() == null || registered.nextExecuteAt().isAfter(now)) {
            throw new CommonException(INVALID_STATE);
        }
        String tenant = TenantUtil.getTenantId();
        var work = transactions.readSnapshot(tenant, () -> {
            var stored = batches.findRun(tenant, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!task.bizId().equals(stored.batch().runId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            if (terminal(stored.batch())) {
                return java.util.Optional.<Work>empty();
            }
            if (stored.batch().state() != ReconciliationState.RUNNING) {
                throw new CommonException(INVALID_STATE);
            }
            var channel = inputs.loadSealed(tenant, stored.batch().channel().artifactId());
            var ledger = inputs.loadSealed(tenant, stored.batch().ledger().artifactId());
            if (!stored.batch().channel().equals(channel.manifest()) || !stored.batch().ledger().equals(ledger.manifest())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return java.util.Optional.of(new Work(stored, channel, ledger));
        });
        if (work.isEmpty()) {
            return;
        }
        var snapshot = work.get();
        var compared = matcher.compare(snapshot.channel(), snapshot.ledger());
        transactions.writeLocal(tenant, () -> {
            var current = batches.lock(tenant, snapshot.stored().batch().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var next = current.batch().complete(task.bizId(), compared);
            if (next == current.batch()) {
                return false;
            }
            results.insert(tenant, current.batch().id(), task.bizId(), compared);
            batches.update(tenant, converter.withBatch(current, next), current.batch().version());
            return true;
        });
    }

    private boolean terminal(ReconciliationBatch batch) {
        return batch.state() == ReconciliationState.MATCHED || batch.state() == ReconciliationState.DIFFERENCES;
    }

    private record Work(StoredReconciliationBatch stored, ReconciliationDataset channel, ReconciliationDataset ledger) {
        @Override
        public String toString() {
            return io.github.loadup.commons.json.ToStringAsJson.reflectionToString(this);
        }
    }
}
