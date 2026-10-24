/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.acquire.app.converter.MerchantNotificationQueryConverter;
import io.github.loadup.pay.acquire.app.model.MerchantNotificationWakeDecision;
import io.github.loadup.pay.acquire.app.security.*;
import io.github.loadup.pay.acquire.client.command.MerchantNotificationWakeCommand;
import io.github.loadup.pay.acquire.client.dto.MerchantNotificationWakeDTO;
import io.github.loadup.pay.acquire.client.facade.MerchantNotificationOperationsFacade;
import io.github.loadup.pay.acquire.client.query.MerchantNotificationWakePageQuery;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

/** Audited native-task recovery within the original delivery policy; contains no HTTP sending path. */
@Service
public class MerchantNotificationOperationsService implements MerchantNotificationOperationsFacade {
    private final MerchantNotificationAccess access;
    private final MerchantNotificationGateway deliveries;
    private final MerchantNotificationWakeGateway receipts;
    private final DelayedTaskTemplate tasks;
    private final MerchantNotificationQueryConverter converter;
    private final OrderCreationTransactions transactions;
    private final IdGenerator ids;
    private final Validator validator;
    private final Clock clock;
    public MerchantNotificationOperationsService(MerchantNotificationAccess access, MerchantNotificationGateway deliveries,
            MerchantNotificationWakeGateway receipts, DelayedTaskTemplate tasks, MerchantNotificationQueryConverter converter,
            OrderCreationTransactions transactions, IdGenerator ids, Validator validator, @Qualifier("acquireClock") Clock clock) {
        this.access = access; this.deliveries = deliveries; this.receipts = receipts; this.tasks = tasks; this.converter = converter;
        this.transactions = transactions; this.ids = ids; this.validator = validator; this.clock = clock;
    }
    @Override public MerchantNotificationWakeDTO wake(MerchantNotificationWakeCommand command) {
        var actor = access.operator();
        validate(command);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        String scope = DigestUtils.sha256(JsonUtil.toJson(List.of("notification-operator-v1", actor.tenantId(), actor.principalType(), actor.principalId())));
        String digest = DigestUtils.sha256(JsonUtil.toJson(List.of("notification-wake-v1", actor.tenantId(), scope,
                command.id(), command.requestKey(), command.expectedVersion(), command.reason())));
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(command.id()); DomainChecks.text(command.requestKey(), 128); DomainChecks.text(command.reason(), 500);
            var known = receipts.find(actor.tenantId(), scope, command.requestKey());
            if (known.isPresent()) return replay(known.get(), actor, scope, digest);
            return transactions.write(actor.tenantId(), () -> {
                var existing = receipts.find(actor.tenantId(), scope, command.requestKey());
                if (existing.isPresent()) return replay(existing.get(), actor, scope, digest);
                var delivery = deliveries.lock(actor.tenantId(), command.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
                if (delivery.rowVersion() != command.expectedVersion()) throw new CommonException(VERSION_CONFLICT);
                var at = now();
                var decision = resume(actor.tenantId(), delivery, at);
                var receipt = converter.toReceipt(ids.generate(), command, delivery, actor, decision, scope, digest, at);
                receipts.insert(actor.tenantId(), receipt);
                return converter.toDTO(receipt);
            });
        });
    }
    @Override public PageDTO<MerchantNotificationWakeDTO> wakes(MerchantNotificationWakePageQuery query) {
        var actor = access.operator();
        validate(query);
        return transactions.read(actor.tenantId(), () -> {
            DomainChecks.id(query.deliveryId());
            deliveries.find(actor.tenantId(), query.deliveryId()).orElseThrow(() -> new CommonException(NOT_FOUND));
            return PageDTO.of(receipts.page(actor.tenantId(), query.deliveryId(), query.page(), query.size()).map(converter::toDTO));
        });
    }
    private MerchantNotificationWakeDecision resume(String tenant, MerchantNotificationDelivery delivery, LocalDateTime at) {
        try {
            return schedule(tenant, delivery, at);
        } catch (CommonException failure) {
            if (failure.getResultCode() == DelayedTaskResultCodeEnum.INVALID_STATE) throw new CommonException(CONCURRENT_UPDATE);
            if (failure.getResultCode() == DelayedTaskResultCodeEnum.CONFLICT) throw new CommonException(SNAPSHOT_INVALID);
            throw failure;
        }
    }
    private MerchantNotificationWakeDecision schedule(String tenant, MerchantNotificationDelivery delivery, LocalDateTime at) {
        if (delivery.state() == MerchantNotificationState.ACKED) return new MerchantNotificationWakeDecision("ACKED", null);
        if (delivery.expired(at)) return new MerchantNotificationWakeDecision("EXPIRED", null);
        if (delivery.finished() || delivery.attemptCount() >= delivery.policy().retryMaxAttempts())
            return new MerchantNotificationWakeDecision("EXHAUSTED", null);
        if (at.isBefore(delivery.nextAttemptAt())) return new MerchantNotificationWakeDecision("WAITING", null);
        String key = new MerchantNotificationKey(delivery.id(), delivery.attemptCount()).bizId();
        var task = tasks.find(MerchantNotificationService.TYPE, key).orElse(null);
        boolean missing = task == null;
        if (missing) task = tasks.register(MerchantNotificationService.TYPE, key, delivery.nextAttemptAt());
        if (task == null || task.status() == null || !tenant.equals(task.tenantId()) || !MerchantNotificationService.TYPE.equals(task.taskType())
                || !key.equals(task.bizId()) || !delivery.nextAttemptAt().equals(task.executeAt())) throw new CommonException(SNAPSHOT_INVALID);
        String result;
        switch (task.status()) {
            case PENDING -> result = missing ? "REGISTERED" : "PENDING";
            case RUNNING -> result = "RUNNING";
            case FAILED -> {
                tasks.retryFailed(MerchantNotificationService.TYPE, key);
                result = "RETRIED";
            }
            case CANCELLED -> {
                tasks.reschedule(MerchantNotificationService.TYPE, key, delivery.nextAttemptAt());
                result = "RESCHEDULED";
            }
            case SUCCEEDED -> throw new CommonException(SNAPSHOT_INVALID);
            default -> throw new CommonException(SNAPSHOT_INVALID);
        }
        return new MerchantNotificationWakeDecision(result, task.id());
    }
    private MerchantNotificationWakeDTO replay(MerchantNotificationWakeReceipt receipt, MerchantNotificationActor actor, String scope, String digest) {
        if (!scope.equals(receipt.callerScopeHash()) || !actor.principalType().equals(receipt.principalType())
                || !actor.principalId().equals(receipt.principalId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!receipt.requestDigest().equals(digest)) throw new CommonException(IDEMPOTENCY_CONFLICT);
        return converter.toDTO(receipt);
    }
    private void validate(Object value) {
        if (value == null || !validator.validate(value).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
    }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.MICROS), clock.getZone()); }
}
