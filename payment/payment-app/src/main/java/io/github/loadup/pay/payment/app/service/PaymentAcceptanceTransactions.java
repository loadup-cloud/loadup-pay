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
import io.github.loadup.commons.util.TenantUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

@Component
public class PaymentAcceptanceTransactions {
    private final TransactionTemplate transaction;
    private final TransactionTemplate snapshots;

    public PaymentAcceptanceTransactions(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshots = new TransactionTemplate(manager);
        snapshots.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshots.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshots.setReadOnly(true);
    }

    /**
     * REQUIRES_NEW/READ_COMMITTED, at most three local attempts after full rollback. No outer transaction or remote IO.
     */
    public <T> T writeLocal(String tenant, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return withTenantContext(tenant, () -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    return Objects.requireNonNull(transaction.execute(status -> work.get()));
                } catch (DuplicateKeyException | TransientDataAccessException conflict) {
                    if (attempt == 2) throw new CommonException(CONCURRENT_UPDATE);
                }
            }
            throw new AssertionError("Unreachable retry state");
        });
    }

    /**
     * A short consistent local database snapshot. No row locks, mutations, remote IO or outer transaction.
     */
    public <T> T readSnapshot(String tenant, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return withTenantContext(tenant, () -> Objects.requireNonNull(snapshots.execute(status -> work.get())));
    }

    /**
     * Dedicated bounded RR read. The transaction deadline is propagated by Spring/MyBatis to JDBC statements.
     */
    public <T> T readSnapshot(String tenant, int timeoutSeconds, Supplier<T> work) {
        if (timeoutSeconds < 1 || timeoutSeconds > 60) throw new CommonException(INVALID_ARGUMENT);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var bounded = new TransactionTemplate(snapshots.getTransactionManager(), snapshots);
        bounded.setTimeout(timeoutSeconds);
        return withTenantContext(tenant, () -> Objects.requireNonNull(bounded.execute(status -> work.get())));
    }

    /**
     * Tenant scope/error translation only. Creates no transaction; any existing caller transaction remains active.
     */
    public <T> T withTenantContext(String tenant, Supplier<T> work) {
        try {
            return TenantUtil.callWithTenant(tenant, work::get);
        } catch (DomainViolation invalid) {
            throw new CommonException(
                    invalid.reason() == DomainViolation.Reason.CONFLICT ? IDEMPOTENCY_CONFLICT : INVALID_ARGUMENT);
        } catch (java.time.DateTimeException | ArithmeticException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
