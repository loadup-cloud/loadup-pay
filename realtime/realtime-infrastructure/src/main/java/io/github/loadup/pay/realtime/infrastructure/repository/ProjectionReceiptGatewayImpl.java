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
package io.github.loadup.pay.realtime.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.realtime.domain.gateway.ProjectionReceiptGateway;
import io.github.loadup.pay.realtime.domain.model.ProjectionReceipt;
import io.github.loadup.pay.realtime.infrastructure.converter.TradeStorageConverter;
import io.github.loadup.pay.realtime.infrastructure.mapper.ProjectionReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.realtime.infrastructure.dataobject.table.Tables.PROJECTION_RECEIPT_DO;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.*;

@Repository
public class ProjectionReceiptGatewayImpl implements ProjectionReceiptGateway {
    private final ProjectionReceiptDOMapper mapper;
    private final TradeStorageConverter converter;
    public ProjectionReceiptGatewayImpl(ProjectionReceiptDOMapper mapper, TradeStorageConverter converter) { this.mapper = mapper; this.converter = converter; }
    @Override public Optional<ProjectionReceipt> find(String tenant, String producer, String delivery) {
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(PROJECTION_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(PROJECTION_RECEIPT_DO.DELETED.eq(0)).and(PROJECTION_RECEIPT_DO.PRODUCER.eq(producer))
                .and(PROJECTION_RECEIPT_DO.DELIVERY_ID.eq(delivery)));
        try { return Optional.ofNullable(row).map(converter::toReceipt); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public void insert(String tenant, ProjectionReceipt receipt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
