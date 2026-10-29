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
package io.github.loadup.pay.limitcenter.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.app.security.LimitActorProvider;
import io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import jakarta.validation.Validator;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DateTimeException;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.EXCEEDED;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_ARGUMENT;

@Component
public class LimitTransactions {
    private final LimitActorProvider actors;
    private final Validator validator;
    private final TransactionTemplate transaction;

    public LimitTransactions(LimitActorProvider actors, Validator validator, PlatformTransactionManager manager) {
        this.actors = actors;
        this.validator = validator;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public LimitActor authorize(Object request, String permission) {
        if (request == null || !validator.validate(request).isEmpty()) throw new CommonException(INVALID_ARGUMENT);
        LimitActor actor = actors.current();
        if (!actor.permissions().contains(permission)) throw new CommonException(ACTION_FORBIDDEN);
        return actor;
    }

    /**
     * REQUIRES_NEW/READ_COMMITTED, at most three local attempts after full rollback. No outer transaction or remote IO.
     */
    public <T> T writeLocal(LimitActor actor, LimitCenterResultCodeEnum conflict, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(CONCURRENT_UPDATE);
        return withTenantContext(actor, conflict, () -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    return Objects.requireNonNull(transaction.execute(status -> work.get()));
                } catch (LimitWriteRaceException | TransientDataAccessException race) {
                    // Every retry starts a fresh transaction, after the previous attempt has fully rolled back.
                    if (attempt == 2) throw new CommonException(CONCURRENT_UPDATE);
                    LockSupport.parkNanos(ThreadLocalRandom.current().nextLong(1_000_000L, 4_000_000L << attempt));
                    if (Thread.currentThread().isInterrupted()) throw new CommonException(CONCURRENT_UPDATE, race);
                }
            }
            throw new AssertionError("Unreachable retry state");
        });
    }

    /**
     * Tenant scope/error translation only. Creates no transaction; any existing caller transaction remains active.
     */
    public <T> T withTenantContext(LimitActor actor, Supplier<T> work) {
        return withTenantContext(actor, IDEMPOTENCY_CONFLICT, work);
    }

    private <T> T withTenantContext(LimitActor actor, LimitCenterResultCodeEnum conflict, Supplier<T> work) {
        try {
            return TenantUtil.callWithTenant(actor.tenantId(), work::get);
        } catch (DomainViolation violation) {
            throw new CommonException(
                    switch (violation.reason()) {
                        case AMOUNT_EXCEEDED -> EXCEEDED;
                        case CONFLICT -> conflict;
                        default -> INVALID_ARGUMENT;
                    });
        } catch (DateTimeException | ArithmeticException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
