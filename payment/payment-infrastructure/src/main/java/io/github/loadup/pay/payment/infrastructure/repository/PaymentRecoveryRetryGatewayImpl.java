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
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryRetryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryRetryReceipt;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentRecoveryRetryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRecoveryRetryReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_RECOVERY_RETRY_RECEIPT_DO;

@Repository
public class PaymentRecoveryRetryGatewayImpl implements PaymentRecoveryRetryGateway {
    private final PaymentRecoveryRetryReceiptDOMapper mapper;
    private final PaymentRecoveryRetryStorageConverter converter;

    public PaymentRecoveryRetryGatewayImpl(PaymentRecoveryRetryReceiptDOMapper mapper, PaymentRecoveryRetryStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<PaymentRecoveryRetryReceipt> find(String tenant, String callerScopeHash, String requestKey) {
        var row = mapper.selectOneByQuery(scope(tenant).and(PAYMENT_RECOVERY_RETRY_RECEIPT_DO.CALLER_SCOPE_HASH.eq(callerScopeHash))
                .and(PAYMENT_RECOVERY_RETRY_RECEIPT_DO.REQUEST_KEY.eq(requestKey)));
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentRecoveryRetryReceipt receipt) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private QueryWrapper scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return QueryWrapper.create().where(PAYMENT_RECOVERY_RETRY_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_RECOVERY_RETRY_RECEIPT_DO.DELETED.eq(0));
    }
}
