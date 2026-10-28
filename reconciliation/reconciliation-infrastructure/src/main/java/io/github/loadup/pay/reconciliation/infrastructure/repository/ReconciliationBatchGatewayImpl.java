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
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationResultSummary;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationBatchDO;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationBatchDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_BATCH_DO;

@Repository
public class ReconciliationBatchGatewayImpl implements ReconciliationBatchGateway {
    private final ReconciliationBatchDOMapper mapper;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationStorageConverter converter;

    public ReconciliationBatchGatewayImpl(ReconciliationBatchDOMapper mapper, ReconciliationInputGateway inputs, ReconciliationStorageConverter converter) {
        this.mapper = mapper;
        this.inputs = inputs;
        this.converter = converter;
    }

    @Override
    public Optional<StoredReconciliationBatch> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(caller);
        io.github.loadup.commons.validation.DomainChecks.text(requestKey, 128);
        return optional(tenant, mapper.selectOneByQuery(scope(tenant).and(RECONCILIATION_BATCH_DO.CALLER_SERVICE_ID.eq(caller))
                .and(RECONCILIATION_BATCH_DO.REQUEST_KEY.eq(requestKey))));
    }

    @Override
    public Optional<StoredReconciliationBatch> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        return optional(tenant, mapper.selectOneByQuery(key(tenant, id)));
    }

    @Override
    public Optional<StoredReconciliationBatch> findRun(String tenant, String runId) {
        ReconciliationStoreContext.readable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(runId);
        return optional(tenant, mapper.selectOneByQuery(scope(tenant).and(RECONCILIATION_BATCH_DO.RUN_ID.eq(runId))));
    }

    @Override
    public Optional<StoredReconciliationBatch> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        return optional(tenant, mapper.selectOneByQuery(key(tenant, id).forUpdate()));
    }

    @Override
    public void insert(String tenant, StoredReconciliationBatch value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.batch().state() != ReconciliationState.INIT || value.batch().version() != 0
                || value.batch().channel() != null || value.batch().ledger() != null)
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, StoredReconciliationBatch value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.batch().version() != Math.incrementExact(expectedVersion))
            throw new CommonException(INVALID_STATE);
        var before = optional(tenant, mapper.selectOneByQuery(key(tenant, value.batch().id()).forUpdate()))
                .orElseThrow(() -> new CommonException(NOT_FOUND));
        if (before.batch().version() != expectedVersion) throw new CommonException(CONCURRENT_UPDATE);
        if (!before.batch().scope().equals(value.batch().scope()) || !before.callerServiceId().equals(value.callerServiceId())
                || !before.requestKey().equals(value.requestKey()) || !before.requestDigest().equals(value.requestDigest())
                || !before.channelAccountCode().equals(value.channelAccountCode())
                || before.batch().channel() != null && !before.batch().channel().equals(value.batch().channel())
                || before.batch().ledger() != null && !before.batch().ledger().equals(value.batch().ledger()))
            throw new CommonException(SNAPSHOT_INVALID);
        boolean allowed = switch (before.batch().state()) {
            case INIT ->
                    (value.batch().state() == ReconciliationState.INIT || value.batch().state() == ReconciliationState.READY)
                            && ((before.batch().channel() == null && value.batch().channel() != null ? 1 : 0)
                            + (before.batch().ledger() == null && value.batch().ledger() != null ? 1 : 0) == 1);
            case READY -> value.batch().state() == ReconciliationState.RUNNING;
            case RUNNING ->
                    (value.batch().state() == ReconciliationState.MATCHED || value.batch().state() == ReconciliationState.DIFFERENCES)
                            && before.batch().runId().equals(value.batch().runId());
            default -> false;
        };
        if (!allowed) throw new CommonException(INVALID_STATE);
        if (mapper.updateByQuery(converter.toDO(tenant, value), true,
                key(tenant, value.batch().id()).and(RECONCILIATION_BATCH_DO.ROW_VERSION.eq(expectedVersion))) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private Optional<StoredReconciliationBatch> optional(String tenant, ReconciliationBatchDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null) throw new CommonException(SNAPSHOT_INVALID);
            var channel = row.getChannelInputId() == null ? null : inputs.sealedManifest(tenant, row.getChannelInputId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var ledger = row.getLedgerInputId() == null ? null : inputs.sealedManifest(tenant, row.getLedgerInputId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            ReconciliationResultSummary summary = null;
            if (row.getMatchedCount() != null || row.getDifferenceCount() != null || row.getResultHash() != null) {
                if (row.getMatchedCount() == null || row.getDifferenceCount() == null || row.getResultHash() == null)
                    throw new CommonException(SNAPSHOT_INVALID);
                summary = converter.toSummary(row);
            }
            return Optional.of(converter.toStored(row, converter.toBatch(row, channel, ledger, summary)));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(RECONCILIATION_BATCH_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_BATCH_DO.DELETED.eq(0));
    }

    private QueryWrapper key(String tenant, String id) {
        io.github.loadup.commons.validation.DomainChecks.id(id);
        return scope(tenant).and(RECONCILIATION_BATCH_DO.ID.eq(id));
    }
}
