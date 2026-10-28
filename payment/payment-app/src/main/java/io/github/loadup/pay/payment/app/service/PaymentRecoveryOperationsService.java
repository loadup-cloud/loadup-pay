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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTaskResultCodeEnum;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.payment.app.converter.PaymentRecoveryOperationsConverter;
import io.github.loadup.pay.payment.app.model.PaymentRecoveryInspection;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.app.security.PaymentActorProvider;
import io.github.loadup.pay.payment.client.command.PaymentRecoveryRetryCommand;
import io.github.loadup.pay.payment.client.dto.PaymentRecoveryRetryDTO;
import io.github.loadup.pay.payment.client.dto.PaymentRecoveryTaskDTO;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.pay.payment.client.facade.PaymentRecoveryOperationsFacade;
import io.github.loadup.pay.payment.client.query.PaymentRecoveryTaskQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryRetryGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKey;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryRetryReceipt;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundRecoveryKey;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.VERSION_CONFLICT;

/**
 * Operational native FAILED retry, separate from original collection/refund execution rights and evidence.
 */
@Service
public class PaymentRecoveryOperationsService implements PaymentRecoveryOperationsFacade {
    private final PaymentActorProvider actors;
    private final PaymentRecoveryRetryGateway receipts;
    private final PaymentAcceptanceGateway payments;
    private final PaymentExecutionLocks paymentLocks;
    private final PaymentRecoveryGateway paymentCursors;
    private final RefundExecutionLocks refundLocks;
    private final RefundGateway refunds;
    private final RefundRecoveryGateway refundCursors;
    private final DelayedTaskTemplate tasks;
    private final PaymentRecoveryOperationsConverter converter;
    private final PaymentAcceptanceTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    private final String operationsService;

    public PaymentRecoveryOperationsService(PaymentActorProvider actors, PaymentRecoveryRetryGateway receipts,
                                            PaymentAcceptanceGateway payments, PaymentExecutionLocks paymentLocks, PaymentRecoveryGateway paymentCursors,
                                            RefundExecutionLocks refundLocks, RefundGateway refunds, RefundRecoveryGateway refundCursors, DelayedTaskTemplate tasks,
                                            PaymentRecoveryOperationsConverter converter, PaymentAcceptanceTransactions transactions,
                                            IdGenerator ids, Validator validator, @Qualifier("paymentClock") Clock clock,
                                            @Value("${loadup.modules.payment.recovery-operations-service-id:pay-ops}") String operationsService) {
        this.actors = actors;
        this.receipts = receipts;
        this.payments = payments;
        this.paymentLocks = paymentLocks;
        this.paymentCursors = paymentCursors;
        this.refundLocks = refundLocks;
        this.refunds = refunds;
        this.refundCursors = refundCursors;
        this.tasks = tasks;
        this.converter = converter;
        this.transactions = transactions;
        this.ids = ids;
        this.validator = validator;
        this.clock = clock;
        this.operationsService = DomainChecks.id(operationsService);
    }

    @Override
    public PaymentRecoveryTaskDTO detail(PaymentRecoveryTaskQuery query) {
        var actor = actor("pay:payment:recovery:read");
        validate(query);
        identifier(query.subjectId());
        return transactions.readSnapshot(actor.tenantId(), () -> converter.toDTO(
                readInspection(actor.tenantId(), query.kind(), query.subjectId(), query.generation())));
    }

    @Override
    public PaymentRecoveryRetryDTO retry(PaymentRecoveryRetryCommand command) {
        var actor = actor("pay:payment:recovery:retry");
        validate(command);
        identifier(command.subjectId());
        try {
            DomainChecks.id(command.expectedTaskId());
        } catch (io.github.loadup.commons.error.DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var expectedAt = BusinessTime.local(command.expectedNextExecuteAt());
        if (expectedAt.getNano() % 1000 != 0 || expectedAt.getYear() < 1970 || expectedAt.getYear() > 9998)
            throw new CommonException(INVALID_ARGUMENT);
        String scope = DigestUtils.sha256(JsonUtil.toJson(List.of("payment-recovery-operator-v1", actor.tenantId(), actor.serviceId())));
        String digest = DigestUtils.sha256(JsonUtil.toJson(List.of("payment-recovery-retry-v1", actor.tenantId(), scope,
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

    private PaymentRecoveryInspection lockForRetry(String tenant, PaymentRecoveryKind kind, String subject, long generation) {
        final String orderId;
        final long cursorGeneration;
        final LocalDateTime dueAt;
        final boolean completed;
        final boolean terminal;
        switch (kind) {
            case COLLECTION -> {
                var initial = payments.find(tenant, subject).orElseThrow(() -> new CommonException(NOT_FOUND));
                // The persisted calling service only validates the original binding; it is not the operator's authority.
                var original = paymentLocks.lock(tenant, subject, initial.callerServiceId()).payment();
                if (!original.payment().id().equals(subject) || !original.payment().orderId().equals(initial.payment().orderId()))
                    throw new CommonException(SNAPSHOT_INVALID);
                var cursor = paymentCursors.lock(tenant, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!cursor.paymentId().equals(subject)) throw new CommonException(SNAPSHOT_INVALID);
                orderId = original.payment().orderId();
                cursorGeneration = cursor.generation();
                dueAt = cursor.dueAt();
                completed = cursor.completed();
                terminal = PaymentExecutionService.terminal(original.payment().state());
            }
            case REFUND_REQUEST -> {
                var original = refundLocks.request(tenant, subject);
                if (!original.request().id().equals(subject)) throw new CommonException(SNAPSHOT_INVALID);
                var cursor = refundCursors.lock(tenant, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!cursor.requestId().equals(subject)) throw new CommonException(SNAPSHOT_INVALID);
                orderId = original.request().orderId();
                cursorGeneration = cursor.generation();
                dueAt = cursor.dueAt();
                completed = cursor.completed();
                terminal = original.request().children().stream().allMatch(RefundOrder::terminal);
            }
            default -> throw new CommonException(INVALID_ARGUMENT);
        }
        return inspection(tenant, kind, subject, generation, new RecoveryView(orderId, cursorGeneration, dueAt, completed, terminal));
    }

    /**
     * Nonlocking reads only. Snapshot isolation, not execution locks, supplies the bounded multi-table view.
     */
    private PaymentRecoveryInspection readInspection(String tenant, PaymentRecoveryKind kind, String subject, long generation) {
        final RecoveryView view;
        switch (kind) {
            case COLLECTION -> {
                var original = payments.find(tenant, subject).orElseThrow(() -> new CommonException(NOT_FOUND));
                var cursor = paymentCursors.find(tenant, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!subject.equals(original.payment().id()) || !subject.equals(cursor.paymentId()))
                    throw new CommonException(SNAPSHOT_INVALID);
                view = new RecoveryView(original.payment().orderId(), cursor.generation(), cursor.dueAt(), cursor.completed(),
                        PaymentExecutionService.terminal(original.payment().state()));
            }
            case REFUND_REQUEST -> {
                var original = refunds.load(tenant, subject);
                var cursor = refundCursors.find(tenant, subject).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                if (!subject.equals(original.request().id()) || !subject.equals(cursor.requestId()))
                    throw new CommonException(SNAPSHOT_INVALID);
                view = new RecoveryView(original.request().orderId(), cursor.generation(), cursor.dueAt(), cursor.completed(),
                        original.request().children().stream().allMatch(RefundOrder::terminal));
            }
            default -> throw new CommonException(INVALID_ARGUMENT);
        }
        return inspection(tenant, kind, subject, generation, view);
    }

    private PaymentRecoveryInspection inspection(String tenant, PaymentRecoveryKind kind, String subject, long generation, RecoveryView view) {
        if (generation > view.generation() || view.completed() && !view.terminal())
            throw new CommonException(SNAPSHOT_INVALID);
        String type = kind == PaymentRecoveryKind.COLLECTION ? PaymentRecoveryService.TYPE : RefundRecoveryService.TYPE;
        String bizId = kind == PaymentRecoveryKind.COLLECTION ? new PaymentRecoveryKey(subject, generation).bizId()
                : new RefundRecoveryKey(subject, generation).bizId();
        var task = tasks.find(type, bizId).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (!tenant.equals(task.tenantId()) || !type.equals(task.taskType()) || !bizId.equals(task.bizId()) || task.status() == null
                || generation == view.generation() && !view.dueAt().equals(task.executeAt()))
            throw new CommonException(SNAPSHOT_INVALID);
        boolean current = generation == view.generation() && !view.completed() && !view.terminal();
        return new PaymentRecoveryInspection(kind, subject, generation, view.orderId(), task, current,
                current && task.status() == DelayedTaskStatus.FAILED && !LocalDateTime.now(clock).isBefore(task.executeAt()));
    }

    private PaymentRecoveryRetryDTO replay(PaymentActor actor, PaymentRecoveryRetryReceipt receipt, String scope, String digest) {
        if (!actor.serviceId().equals(receipt.callerServiceId()) || !scope.equals(receipt.callerScopeHash()))
            throw new CommonException(ACTION_FORBIDDEN);
        if (!digest.equals(receipt.requestDigest())) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toDTO(receipt);
    }

    private PaymentActor actor(String permission) {
        var actor = actors.current();
        if (!operationsService.equals(actor.serviceId()) || !actor.permissions().contains(permission))
            throw new CommonException(ACTION_FORBIDDEN);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return actor;
    }

    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }

    private void identifier(String subject) {
        try {
            DomainChecks.id(subject);
        } catch (io.github.loadup.commons.error.DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private record RecoveryView(String orderId, long generation, LocalDateTime dueAt, boolean completed,
                                boolean terminal) {
        @Override
        public String toString() {
            return io.github.loadup.commons.json.ToStringAsJson.reflectionToString(this);
        }
    }
}
