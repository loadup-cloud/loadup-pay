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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.util.TenantUtil;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT;

@Component
public class PublicationTransactions {
    private final TransactionTemplate transaction;

    public PublicationTransactions(PlatformTransactionManager manager) {
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * REQUIRES_NEW/READ_COMMITTED, at most three local attempts after full rollback. No outer transaction or remote IO.
     */
    public <T> T writeLocal(String tenant, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        return withTenantContext(tenant, () -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    return Objects.requireNonNull(transaction.execute(status -> work.get()));
                } catch (TransientDataAccessException conflict) {
                    if (attempt == 2) {
                        throw new CommonException(POLICY_PUBLICATION_CONFLICT);
                    }
                    LockSupport.parkNanos(ThreadLocalRandom.current().nextLong(1_000_000L, 4_000_000L << attempt));
                    if (Thread.currentThread().isInterrupted()) {
                        throw new CommonException(POLICY_PUBLICATION_CONFLICT, conflict);
                    }
                }
            }
            throw new AssertionError("Unreachable retry state");
        });
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
        }
    }
}
