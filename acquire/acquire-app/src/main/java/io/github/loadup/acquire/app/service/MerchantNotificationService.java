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
package io.github.loadup.acquire.app.service;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.acquire.app.port.MerchantNotificationTransport;
import io.github.loadup.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.acquire.domain.model.MerchantNotificationKey;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskHandler;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Native leases wake durable attempt generations; delivery is at least once and never changes the event body.
 */
@Service
public class MerchantNotificationService implements DelayedTaskHandler {
    public static final String TYPE = "pay.acquire.merchant-notification";
    private final MerchantNotificationGateway deliveries;
    private final MerchantNotificationTransport transport;
    private final DelayedTaskTemplate tasks;
    private final OrderCreationTransactions transactions;
    private final Clock clock;

    public MerchantNotificationService(
            MerchantNotificationGateway deliveries,
            MerchantNotificationTransport transport,
            DelayedTaskTemplate tasks,
            OrderCreationTransactions transactions,
            Clock clock) {
        this.deliveries = deliveries;
        this.transport = transport;
        this.tasks = tasks;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return TYPE;
    }

    @Override
    public void handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (task == null || !TYPE.equals(task.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        final MerchantNotificationKey key;
        try {
            key = MerchantNotificationKey.parse(task.bizId());
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var nativeTask = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var at = now();
        if (!task.id().equals(nativeTask.id())
                || !TYPE.equals(nativeTask.taskType())
                || !task.bizId().equals(nativeTask.bizId())
                || !task.executeAt().equals(nativeTask.executeAt())
                || nativeTask.status() != DelayedTaskStatus.RUNNING
                || nativeTask.attempts() != task.attempts()
                || nativeTask.leaseUntil() == null
                || !at.isBefore(nativeTask.leaseUntil())
                || at.isBefore(nativeTask.nextExecuteAt())) {
            throw new CommonException(INVALID_STATE);
        }
        String tenant = TenantUtil.getTenantId();
        var sending = transactions
                .writeLocal(tenant, () -> {
                    var before =
                            deliveries.lock(key.deliveryId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    if (before.finished() || key.sequence() < before.attemptCount()) {
                        return Optional.<MerchantNotificationDelivery>empty();
                    }
                    if (key.sequence() != before.attemptCount()
                            || !task.executeAt().equals(before.nextAttemptAt())) {
                        throw new CommonException(SNAPSHOT_INVALID);
                    }
                    var startedAt = now();
                    if (before.expired(startedAt)
                            || before.attemptCount() >= before.policy().retryMaxAttempts()) {
                        deliveries.update(before, before.exhaust());
                        return Optional.<MerchantNotificationDelivery>empty();
                    }
                    String route = null;
                    try {
                        route = transport.fingerprint(before);
                    } catch (RuntimeException unavailable) {
                        /* Missing local configuration consumes no network call. */
                    }
                    var next = before.begin(startedAt, route);
                    deliveries.update(before, next);
                    tasks.register(
                            TYPE,
                            new MerchantNotificationKey(next.id(), next.attemptCount()).bizId(),
                            next.nextAttemptAt());
                    return Optional.of(next);
                })
                .orElse(null);
        if (sending == null) {
            return;
        }
        boolean ack = false;
        try {
            ack = transport.send(sending);
        } catch (RuntimeException unavailable) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
        }
        final boolean acknowledged = ack;
        transactions.writeLocal(tenant, () -> {
            var current = deliveries.lock(key.deliveryId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (current.finished()) {
                return Boolean.TRUE;
            }
            if (!current.eventId().equals(sending.eventId())
                    || !current.bodyDigest().equals(sending.bodyDigest())
                    || current.attemptCount() < sending.attemptCount()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            if (current.expired(now())) {
                deliveries.update(current, current.exhaust());
            } else if (acknowledged) {
                deliveries.update(current, current.acknowledge(now()));
            }
            return Boolean.TRUE;
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone());
    }
}
