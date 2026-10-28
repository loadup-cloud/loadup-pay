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

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStatementStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationStatementDO;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationStatementDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_STATEMENT_DO;

@Repository
public class ReconciliationStatementGatewayImpl implements ReconciliationStatementGateway {
    private final ReconciliationStatementDOMapper mapper;
    private final ReconciliationStatementStorageConverter converter;

    public ReconciliationStatementGatewayImpl(ReconciliationStatementDOMapper mapper, ReconciliationStatementStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationStatementRequest> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        return read(mapper.selectOneByQuery(scope(tenant).and(RECONCILIATION_STATEMENT_DO.CALLER_SERVICE_ID.eq(caller))
                .and(RECONCILIATION_STATEMENT_DO.REQUEST_KEY.eq(requestKey))));
    }

    @Override
    public Optional<ReconciliationStatementRequest> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        return read(mapper.selectOneByQuery(key(tenant, id)));
    }

    @Override
    public Optional<ReconciliationStatementRequest> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        return read(mapper.selectOneByQuery(key(tenant, id).forUpdate()));
    }

    @Override
    public Optional<ReconciliationStatementRequest> activeForBatch(String tenant, String batchId) {
        ReconciliationStoreContext.writable(tenant);
        DomainChecks.id(batchId);
        var rows = mapper.selectListByQuery(scope(tenant).and(RECONCILIATION_STATEMENT_DO.BATCH_ID.eq(batchId))
                .and(RECONCILIATION_STATEMENT_DO.STATE.eq("INIT")).limit(2));
        if (rows.size() > 1) throw new CommonException(SNAPSHOT_INVALID);
        return rows.isEmpty() ? Optional.empty() : read(rows.getFirst());
    }

    @Override
    public void insert(String tenant, ReconciliationStatementRequest value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.state() != ReconciliationStatementState.INIT || value.version() != 0L)
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, ReconciliationStatementRequest value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE || value.version() != expectedVersion + 1L)
            throw new CommonException(INVALID_STATE);
        var original = lock(tenant, value.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (original.version() != expectedVersion) throw new CommonException(CONCURRENT_UPDATE);
        if (!converter.toInitial(original).equals(converter.toInitial(value)) || original.state() != ReconciliationStatementState.INIT
                || value.state() == ReconciliationStatementState.INIT) throw new CommonException(SNAPSHOT_INVALID);
        if (mapper.updateByQuery(converter.toDO(tenant, value), true, key(tenant, value.id())
                .and(RECONCILIATION_STATEMENT_DO.ROW_VERSION.eq(expectedVersion))) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private Optional<ReconciliationStatementRequest> read(ReconciliationStatementDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null || row.getMaxBytes() == null) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper key(String tenant, String id) {
        DomainChecks.id(id);
        return scope(tenant).and(RECONCILIATION_STATEMENT_DO.ID.eq(id));
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(RECONCILIATION_STATEMENT_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_STATEMENT_DO.DELETED.eq(0));
    }
}
