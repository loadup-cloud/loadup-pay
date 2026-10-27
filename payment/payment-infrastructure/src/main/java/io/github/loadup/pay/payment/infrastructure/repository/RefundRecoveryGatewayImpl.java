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
package io.github.loadup.pay.payment.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.domain.gateway.RefundRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.RefundRecoveryCursor;
import io.github.loadup.pay.payment.infrastructure.converter.RefundRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundRecoveryCursorDOMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_RECOVERY_CURSOR_DO;

@Repository
public class RefundRecoveryGatewayImpl implements RefundRecoveryGateway {
    private final RefundRecoveryCursorDOMapper mapper;
    private final RefundRecoveryStorageConverter converter;

    public RefundRecoveryGatewayImpl(RefundRecoveryCursorDOMapper mapper, RefundRecoveryStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<RefundRecoveryCursor> find(String tenant, String requestId) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var row = mapper.selectOneByQuery(scope(tenant, requestId));
        try { return Optional.ofNullable(row).map(converter::toDomain); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }

    @Override
    public Optional<RefundRecoveryCursor> lock(String tenant, String requestId) {
        writable(tenant);
        var row = mapper.selectOneByQuery(scope(tenant, requestId).forUpdate());
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, RefundRecoveryCursor value) {
        writable(tenant);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, RefundRecoveryCursor before, RefundRecoveryCursor after) {
        writable(tenant);
        boolean completed = !before.completed() && after.completed()
                && before.generation() == after.generation() && before.dueAt().equals(after.dueAt());
        boolean rescheduled = !before.completed() && !after.completed() && before.generation() != Long.MAX_VALUE
                && after.generation() == before.generation() + 1 && after.dueAt().isAfter(before.dueAt());
        if (!before.requestId().equals(after.requestId()) || (!completed && !rescheduled))
            throw new CommonException(INVALID_STATE);

        int changed = mapper.updateByQuery(converter.toPatch(after), true,
                scope(tenant, before.requestId()).and(REFUND_RECOVERY_CURSOR_DO.GENERATION.eq(before.generation()))
                        .and(REFUND_RECOVERY_CURSOR_DO.COMPLETED.eq(false)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private QueryWrapper scope(String tenant, String requestId) {
        return QueryWrapper.create().where(REFUND_RECOVERY_CURSOR_DO.TENANT_ID.eq(tenant))
                .and(REFUND_RECOVERY_CURSOR_DO.DELETED.eq(0)).and(REFUND_RECOVERY_CURSOR_DO.ID.eq(requestId));
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
