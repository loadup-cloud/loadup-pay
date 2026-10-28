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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskResultCodeEnum;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.app.converter.AcquireRecoveryConverter;
import io.github.loadup.pay.acquire.app.model.AcquireRecoveryInspection;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActorProvider;
import io.github.loadup.pay.acquire.client.command.AcquireRecoveryRetryCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireRecoveryRetryDTO;
import io.github.loadup.pay.acquire.client.dto.AcquireRecoveryTaskDTO;
import io.github.loadup.pay.acquire.client.enums.AcquireRecoveryKind;
import io.github.loadup.pay.acquire.client.facade.AcquireRecoveryOperationsFacade;
import io.github.loadup.pay.acquire.client.query.AcquireRecoveryTaskQuery;
import io.github.loadup.pay.acquire.domain.gateway.AcquireRecoveryRetryGateway;
import io.github.loadup.pay.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AcquireRecoveryRetryReceipt;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryCursor;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryKey;
import io.github.loadup.pay.acquire.domain.model.OrderTaskKey;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.VERSION_CONFLICT;

/**
 * An audited native FAILED retry, never another financial intent, route, deadline or business execution budget.
 */
@Service
public class AcquireRecoveryOperationsService implements AcquireRecoveryOperationsFacade {
    private final AcquireServiceActorProvider actors;
    private final AcquireRecoveryRetryGateway receipts;
    private final OrderCreationGateway orders;
    private final OrderExtensionGateway extensions;
    private final OrderClosureGateway closes;
    private final PaymentOutcomeGateway allocations;
    private final AllocationRecoveryGateway cursors;
    private final AllocationRecoveryService paymentRecovery;
    private final OrderClosureOperations closure;
    private final DelayedTaskTemplate tasks;
    private final AcquireRecoveryConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String operationsService;

    public AcquireRecoveryOperationsService(AcquireServiceActorProvider actors, AcquireRecoveryRetryGateway receipts,
                                            OrderCreationGateway orders, OrderExtensionGateway extensions, OrderClosureGateway closes,
                                            PaymentOutcomeGateway allocations, AllocationRecoveryGateway cursors, AllocationRecoveryService paymentRecovery,
                                            OrderClosureOperations closure, DelayedTaskTemplate tasks, AcquireRecoveryConverter converter,
                                            OrderCreationTransactions transactions, IdGenerator ids, Validator validator, @Qualifier("acquireClock") Clock clock,
                                            @Value("${loadup.modules.acquire.recovery-operations-service-id:pay-ops}") String operationsService) {
        this.actors = actors;
        this.receipts = receipts;
        this.orders = orders;
        this.extensions = extensions;
        this.closes = closes;
        this.allocations = allocations;
        this.cursors = cursors;
        this.paymentRecovery = paymentRecovery;
        this.closure = closure;
        this.tasks = tasks;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.operationsService = DomainChecks.id(operationsService);
    }

    @Override
    public AcquireRecoveryTaskDTO detail(AcquireRecoveryTaskQuery query) {
        var actor = actor("pay:acquire:recovery:read");
        validate(query);
        key(query.kind(), query.subjectId(), query.generation());
        return transactions.readSnapshot(actor.tenantId(), () -> converter.toDTO(
                readInspection(actor.tenantId(), query.kind(), query.subjectId(), query.generation())));
    }

    @Override
    public AcquireRecoveryRetryDTO retry(AcquireRecoveryRetryCommand command) {
        var actor = actor("pay:acquire:recovery:retry");
        validate(command);
        key(command.kind(), command.subjectId(), command.generation());
        try {
            DomainChecks.id(command.expectedTaskId());
        } catch (io.github.loadup.commons.error.DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var expectedAt = BusinessTime.local(command.expectedNextExecuteAt());
        if (expectedAt.getNano() % 1000 != 0 || expectedAt.getYear() < 1970 || expectedAt.getYear() > 9998)
            throw new CommonException(INVALID_ARGUMENT);
        String scope = DigestUtils.sha256(JsonUtil.toJson(List.of("acquire-recovery-operator-v1", actor.tenantId(), actor.serviceId())));
        String digest = DigestUtils.sha256(JsonUtil.toJson(List.of("acquire-recovery-retry-v1", actor.tenantId(), scope,
                command.kind().name(), command.subjectId(), command.generation(), command.expectedTaskId(), command.expectedAttempts(),
                expectedAt.toString(), command.requestKey(), command.reason())));
        return transactions.withTenantContext(actor.tenantId(), () -> {
            var known = receipts.find(actor.tenantId(), scope, command.requestKey());
            if (known.isPresent()) return replay(actor, known.get(), scope, digest);
            return transactions.writeLocal(actor.tenantId(), () -> {
                var existing = receipts.find(actor.tenantId(), scope, command.requestKey());
                if (existing.isPresent()) return replay(actor, existing.get(), scope, digest);
                var inspection = lockForRetry(actor.tenantId(), command.kind(), command.subjectId(), command.generation());
                // Serializes competing retry requests for the same order; the ledger uniqueness also covers cross-order key reuse.
                existing = receipts.find(actor.tenantId(), scope, command.requestKey());
                if (existing.isPresent()) return replay(actor, existing.get(), scope, digest);
                var task = inspection.task();
                if (!task.id().equals(command.expectedTaskId()) || task.attempts() != command.expectedAttempts()
                        || !task.nextExecuteAt().equals(expectedAt)) throw new CommonException(VERSION_CONFLICT);
                if (task.status() != DelayedTaskStatus.FAILED) throw new CommonException(INVALID_STATE);
                String result = "NO_LONGER_NEEDED";
                if (inspection.businessCurrent()) {
                    if (!inspection.retryable()) throw new CommonException(INVALID_STATE);
                    try {
                        tasks.retryFailed(task.taskType(), task.bizId());
                    } catch (CommonException changed) {
                        if (changed.getResultCode() == DelayedTaskResultCodeEnum.INVALID_STATE)
                            throw new CommonException(CONCURRENT_UPDATE);
                        throw changed;
                    }
                    result = "RETRIED";
                }
                var receipt = converter.toReceipt(ids.generate(), command, actor, inspection, scope, digest, result,
                        LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
                receipts.insert(actor.tenantId(), receipt);
                return converter.toDTO(receipt);
            });
        });
    }

    private AcquireRecoveryInspection readInspection(String tenant, AcquireRecoveryKind kind, String subject, long generation) {
        var located = kind == AcquireRecoveryKind.PAYMENT_ALLOCATION
                ? cursors.find(tenant, subject).orElseThrow(() -> new CommonException(NOT_FOUND)) : null;
        String orderId = located == null ? subject : located.orderId();
        var order = orders.findOrder(tenant, orderId).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!order.order().id().equals(orderId)) throw new CommonException(SNAPSHOT_INVALID);
        var task = findTask(tenant, kind, subject, generation);
        boolean current = switch (kind) {
            case PAYMENT_ALLOCATION -> allocationCurrent(tenant, order,
                    allocations.findPayment(tenant, orderId, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)),
                    located, subject, generation, task);
            case ORDER_DEADLINE -> deadlineCurrent(tenant, order, generation, task);
            case ORDER_CLOSURE -> closureCurrent(tenant, order, generation, task);
        };
        return inspection(kind, subject, generation, orderId, task, current);
    }

    private AcquireRecoveryInspection lockForRetry(String tenant, AcquireRecoveryKind kind, String subject, long generation) {
        String orderId = kind == AcquireRecoveryKind.PAYMENT_ALLOCATION
                ? cursors.find(tenant, subject).orElseThrow(() -> new CommonException(NOT_FOUND)).orderId() : subject;
        var order = orders.lockOrder(tenant, orderId);
        if (!order.order().id().equals(orderId)) throw new CommonException(SNAPSHOT_INVALID);
        var task = findTask(tenant, kind, subject, generation);
        boolean current = switch (kind) {
            case PAYMENT_ALLOCATION -> allocationCurrent(tenant, order,
                    allocations.lockPayment(tenant, orderId, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)),
                    cursors.lock(tenant, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)), subject, generation, task);
            case ORDER_DEADLINE -> deadlineCurrent(tenant, order, generation, task);
            case ORDER_CLOSURE -> closureCurrent(tenant, order, generation, task);
        };
        return inspection(kind, subject, generation, orderId, task, current);
    }

    private DelayedTask findTask(String tenant, AcquireRecoveryKind kind, String subject, long generation) {
        String type = switch (kind) {
            case PAYMENT_ALLOCATION -> AllocationRecoveryQueue.TYPE;
            case ORDER_DEADLINE -> OrderDeadlineTasks.TYPE;
            case ORDER_CLOSURE -> OrderClosureTasks.TYPE;
        };
        String bizId = kind == AcquireRecoveryKind.PAYMENT_ALLOCATION
                ? new AllocationRecoveryKey(subject, generation).bizId() : new OrderTaskKey(subject, generation).bizId();
        var task = tasks.find(type, bizId).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!tenant.equals(task.tenantId()) || !type.equals(task.taskType()) || !bizId.equals(task.bizId()) || task.status() == null)
            throw new CommonException(SNAPSHOT_INVALID);
        return task;
    }

    private boolean allocationCurrent(String tenant, StoredAcquireOrder order, StoredPaymentAllocation allocation,
                                      AllocationRecoveryCursor cursor, String subject, long generation, DelayedTask task) {
        if (!cursor.paymentId().equals(subject)) throw new CommonException(SNAPSHOT_INVALID);
        boolean current = paymentRecovery.isCurrent(tenant, order, allocation, cursor, generation);
        if (generation == cursor.generation() && !cursor.dueAt().equals(task.executeAt()))
            throw new CommonException(SNAPSHOT_INVALID);
        return current;
    }

    private boolean deadlineCurrent(String tenant, StoredAcquireOrder order, long generation, DelayedTask task) {
        long version = extensions.deadlineVersion(tenant, order.order());
        if (generation > version || generation == version && !order.order().orderDueAt().equals(task.executeAt()))
            throw new CommonException(SNAPSHOT_INVALID);
        return generation == version && (order.order().state() == AcquireOrderState.INIT || order.order().state() == AcquireOrderState.PENDING);
    }

    private boolean closureCurrent(String tenant, StoredAcquireOrder order, long generation, DelayedTask task) {
        var intent = closes.findIntent(tenant, order.order().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        boolean current = closure.isCurrent(new OrderPaymentScope(tenant, order.callerScopeHash()), order, intent, generation);
        if (task.executeAt().isBefore(intent.requestedAt())) throw new CommonException(SNAPSHOT_INVALID);
        return current;
    }

    private AcquireRecoveryInspection inspection(AcquireRecoveryKind kind, String subject, long generation, String orderId,
                                                 DelayedTask task, boolean current) {
        return new AcquireRecoveryInspection(kind, subject, generation, orderId, task, current,
                current && task.status() == DelayedTaskStatus.FAILED && !LocalDateTime.now(clock).isBefore(task.executeAt()));
    }

    private AcquireRecoveryRetryDTO replay(AcquireServiceActor actor, AcquireRecoveryRetryReceipt receipt, String scope, String digest) {
        if (!actor.serviceId().equals(receipt.callerServiceId()) || !scope.equals(receipt.callerScopeHash()))
            throw new CommonException(ACTION_FORBIDDEN);
        if (!digest.equals(receipt.requestDigest())) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toDTO(receipt);
    }

    private AcquireServiceActor actor(String permission) {
        var actor = actors.current();
        if (!operationsService.equals(actor.serviceId()) || !actor.permissions().contains(permission))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private void key(AcquireRecoveryKind kind, String subject, long generation) {
        try {
            DomainChecks.id(subject);
        } catch (io.github.loadup.commons.error.DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (kind == AcquireRecoveryKind.ORDER_DEADLINE && generation < 1) throw new CommonException(INVALID_ARGUMENT);
    }
}
