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
package io.github.loadup.pay.reconciliation.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

final class ReconciliationStoreContext {
    private ReconciliationStoreContext() {}
    static void readable(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
    static void writable(String tenant) {
        readable(tenant);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new CommonException(INVALID_STATE);
    }
}
