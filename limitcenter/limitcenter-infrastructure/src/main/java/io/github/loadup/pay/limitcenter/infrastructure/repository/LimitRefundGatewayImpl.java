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
package io.github.loadup.pay.limitcenter.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitRefundGateway;
import io.github.loadup.pay.limitcenter.domain.model.LimitRefundReceipt;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitRefundStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitRefundReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.limitcenter.infrastructure.dataobject.table.Tables.LIMIT_REFUND_RECEIPT_DO;

@Repository
public class LimitRefundGatewayImpl implements LimitRefundGateway {
    private final LimitRefundReceiptDOMapper mapper;
    private final LimitRefundStorageConverter converter;

    public LimitRefundGatewayImpl(LimitRefundReceiptDOMapper mapper, LimitRefundStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<LimitRefundReceipt> find(String tenant, String refundId) {
        scope(tenant);
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(LIMIT_REFUND_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(LIMIT_REFUND_RECEIPT_DO.DELETED.eq(0)).and(LIMIT_REFUND_RECEIPT_DO.REFUND_ID.eq(refundId)));
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, LimitRefundReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void scope(String tenant) {
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
