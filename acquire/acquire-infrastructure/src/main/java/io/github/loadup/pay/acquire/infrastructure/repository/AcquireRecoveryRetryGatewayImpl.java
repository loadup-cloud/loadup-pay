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
package io.github.loadup.pay.acquire.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.domain.gateway.AcquireRecoveryRetryGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireRecoveryRetryReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.AcquireRecoveryRetryStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireRecoveryRetryReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_RECOVERY_RETRY_RECEIPT_DO;

@Repository
public class AcquireRecoveryRetryGatewayImpl implements AcquireRecoveryRetryGateway {
    private final AcquireRecoveryRetryReceiptDOMapper mapper;
    private final AcquireRecoveryRetryStorageConverter converter;

    public AcquireRecoveryRetryGatewayImpl(AcquireRecoveryRetryReceiptDOMapper mapper, AcquireRecoveryRetryStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<AcquireRecoveryRetryReceipt> find(String tenant, String callerScopeHash, String requestKey) {
        var row = mapper.selectOneByQuery(scope(tenant).and(ACQUIRE_RECOVERY_RETRY_RECEIPT_DO.CALLER_SCOPE_HASH.eq(callerScopeHash))
                .and(ACQUIRE_RECOVERY_RETRY_RECEIPT_DO.REQUEST_KEY.eq(requestKey)));
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, AcquireRecoveryRetryReceipt receipt) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private QueryWrapper scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return QueryWrapper.create().where(ACQUIRE_RECOVERY_RETRY_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(ACQUIRE_RECOVERY_RETRY_RECEIPT_DO.DELETED.eq(0));
    }
}
