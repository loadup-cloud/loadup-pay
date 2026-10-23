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
package io.github.loadup.pay.realtime.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.common.DomainViolation;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_STATE;

@Component
public class RealtimeTransactions {
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public RealtimeTransactions(PlatformTransactionManager manager) {
        writes = new TransactionTemplate(manager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        reads = new TransactionTemplate(manager);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reads.setReadOnly(true);
    }

    public <T> T write(String tenant, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return scoped(tenant, () -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    return writes.execute(status -> work.get());
                } catch (DuplicateKeyException | TransientDataAccessException conflict) {
                    if (attempt == 2) throw new CommonException(CONCURRENT_UPDATE);
                }
            }
            throw new AssertionError("Unreachable retry");
        });
    }

    public <T> T read(String tenant, Supplier<T> work) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        return scoped(tenant, () -> reads.execute(status -> work.get()));
    }

    private <T> T scoped(String tenant, Supplier<T> work) {
        try {
            return TenantUtil.callWithTenant(tenant, work::get);
        } catch (DomainViolation invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        } catch (java.time.DateTimeException | ArithmeticException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }
}
