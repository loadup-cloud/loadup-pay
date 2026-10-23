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
import io.github.loadup.pay.acquire.app.model.OrderSnapshotFacts;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderSnapshotStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_ORDER_DO;

@Repository
public class OrderSnapshotReaderImpl implements OrderSnapshotReader {
    private final AcquireOrderDOMapper mapper;
    private final OrderSnapshotStorageConverter converter;

    public OrderSnapshotReaderImpl(AcquireOrderDOMapper mapper, OrderSnapshotStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public OrderSnapshotFacts lockCurrent(String tenant, String orderId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                .and(ACQUIRE_ORDER_DO.DELETED.eq(0)).and(ACQUIRE_ORDER_DO.ID.eq(orderId)).forUpdate());
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        try {
            return converter.toFacts(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
