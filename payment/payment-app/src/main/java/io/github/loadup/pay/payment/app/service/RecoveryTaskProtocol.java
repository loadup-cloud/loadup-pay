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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.pay.payment.domain.model.RecoveryPosition;
import io.github.loadup.pay.payment.domain.model.RecoveryTaskKey;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Pure metadata checks only. Native lookup/claim/heartbeat/ACK and all financial work remain with their owners.
 */
public final class RecoveryTaskProtocol {
    private RecoveryTaskProtocol() {
    }

    public static RecoveryTaskKey receive(String expectedType, DelayedTask task, String trustedTenant) {
        if (task == null || expectedType == null || task.tenantId() == null || !expectedType.equals(task.taskType())
                || !task.tenantId().equals(trustedTenant)) throw new CommonException(SNAPSHOT_INVALID);
        try {
            DomainChecks.id(task.tenantId());
            return RecoveryTaskKey.parse(task.bizId());
        } catch (DomainViolation | NullPointerException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    public static void requireClaim(String expectedType, DelayedTask task, DelayedTask registered, LocalDateTime now) {
        if (task == null || registered == null || now == null || expectedType == null || task.id() == null || task.tenantId() == null || task.bizId() == null
                || task.executeAt() == null || task.status() != DelayedTaskStatus.RUNNING || task.attempts() < 1 || task.maxAttempts() < 1
                || !task.id().equals(registered.id()) || !Objects.equals(task.tenantId(), registered.tenantId())
                || !expectedType.equals(registered.taskType()) || !expectedType.equals(task.taskType())
                || !Objects.equals(task.bizId(), registered.bizId()) || !task.executeAt().equals(registered.executeAt())
                || registered.status() != DelayedTaskStatus.RUNNING || registered.attempts() != task.attempts()
                || registered.maxAttempts() != task.maxAttempts() || registered.leaseUntil() == null || registered.nextExecuteAt() == null
                || !now.isBefore(registered.leaseUntil()) || now.isBefore(registered.nextExecuteAt()))
            throw new CommonException(INVALID_STATE);
    }

    public static boolean current(RecoveryTaskKey key, RecoveryPosition position, LocalDateTime originalExecuteAt) {
        try {
            return Objects.requireNonNull(position).current(key, originalExecuteAt);
        } catch (DomainViolation | NullPointerException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
