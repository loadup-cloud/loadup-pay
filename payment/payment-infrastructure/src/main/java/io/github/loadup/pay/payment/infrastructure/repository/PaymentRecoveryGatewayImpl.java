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
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRecoveryCursorDOMapper;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_RECOVERY_CURSOR_DO;

@Repository
public class PaymentRecoveryGatewayImpl implements PaymentRecoveryGateway {
    private final PaymentRecoveryCursorDOMapper mapper;
    private final PaymentRecoveryStorageConverter converter;

    public PaymentRecoveryGatewayImpl(PaymentRecoveryCursorDOMapper mapper, PaymentRecoveryStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<PaymentRecoveryCursor> find(String tenant, String paymentId) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var row = mapper.selectOneByQuery(scope(tenant, paymentId));
        try { return Optional.ofNullable(row).map(converter::toDomain); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }

    @Override
    public Optional<PaymentRecoveryCursor> lock(String tenant, String paymentId) {
        writable(tenant);
        var row = mapper.selectOneByQuery(scope(tenant, paymentId).forUpdate());
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentRecoveryCursor value) {
        writable(tenant);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, PaymentRecoveryCursor before, PaymentRecoveryCursor after) {
        writable(tenant);
        boolean completed = !before.completed() && after.completed()
                && before.generation() == after.generation() && before.dueAt().equals(after.dueAt());
        boolean rescheduled = !before.completed() && !after.completed() && before.generation() != Long.MAX_VALUE
                && after.generation() == before.generation() + 1 && after.dueAt().isAfter(before.dueAt());
        if (!before.paymentId().equals(after.paymentId()) || (!completed && !rescheduled))
            throw new CommonException(INVALID_STATE);

        int changed = mapper.updateByQuery(converter.toPatch(after), true,
                scope(tenant, before.paymentId()).and(PAYMENT_RECOVERY_CURSOR_DO.GENERATION.eq(before.generation()))
                        .and(PAYMENT_RECOVERY_CURSOR_DO.COMPLETED.eq(false)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private QueryWrapper scope(String tenant, String paymentId) {
        return QueryWrapper.create().where(PAYMENT_RECOVERY_CURSOR_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_RECOVERY_CURSOR_DO.DELETED.eq(0)).and(PAYMENT_RECOVERY_CURSOR_DO.ID.eq(paymentId));
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
