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
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationInputDO;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationEntryDOMapper;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationInputDOMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_NOT_SEALED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_ENTRY_DO;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_INPUT_DO;

@Repository
public class ReconciliationInputGatewayImpl implements ReconciliationInputGateway {
    private final ReconciliationInputDOMapper inputs;
    private final ReconciliationEntryDOMapper entries;
    private final ReconciliationStorageConverter converter;
    private final int maxRecords;

    public ReconciliationInputGatewayImpl(ReconciliationInputDOMapper inputs, ReconciliationEntryDOMapper entries,
                                          ReconciliationStorageConverter converter, @Value("${loadup.modules.reconciliation.max-records:10000}") int maxRecords) {
        if (maxRecords < 1 || maxRecords > 1000000) throw new CommonException(INVALID_ARGUMENT);
        this.inputs = inputs;
        this.entries = entries;
        this.converter = converter;
        this.maxRecords = maxRecords;
    }

    @Override
    public Optional<ReconciliationManifest> sealedManifest(String tenant, String artifactId) {
        ReconciliationStoreContext.readable(tenant);
        var row = inputs.selectOneByQuery(key(tenant, artifactId));
        return row == null || !"SEALED".equals(row.getState()) ? Optional.empty() : Optional.of(manifest(row));
    }

    @Override
    public ReconciliationDataset loadSealed(String tenant, String artifactId) {
        ReconciliationStoreContext.readable(tenant);
        var row = inputs.selectOneByQuery(key(tenant, artifactId));
        if (row == null || !"SEALED".equals(row.getState())) throw new CommonException(INPUT_NOT_SEALED);
        var manifest = manifest(row);
        if (manifest.totals().recordCount() > maxRecords) throw new CommonException(INPUT_INVALID);
        var rows = entries.selectListByQuery(QueryWrapper.create().where(RECONCILIATION_ENTRY_DO.TENANT_ID.eq(tenant))
                .and(RECONCILIATION_ENTRY_DO.DELETED.eq(0)).and(RECONCILIATION_ENTRY_DO.INPUT_ID.eq(artifactId))
                .orderBy(RECONCILIATION_ENTRY_DO.RECORD_NUMBER.asc()).limit(maxRecords + 1));
        try {
            var lines = rows.stream().map(entry -> {
                var line = converter.toLine(entry);
                if (!line.key().fingerprint().equals(entry.getMatchKeyHash()))
                    throw new CommonException(SNAPSHOT_INVALID);
                return line;
            }).toList();
            return new ReconciliationDataset(manifest, lines);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertSealed(String tenant, String fileId, ReconciliationDataset dataset) {
        ReconciliationStoreContext.writable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(fileId);
        if (dataset == null || dataset.records().size() > maxRecords) throw new CommonException(INPUT_INVALID);
        var original = inputs.selectOneByQuery(key(tenant, dataset.manifest().artifactId()).forUpdate());
        if (original != null) {
            if (!fileId.equals(original.getFileId()) || !dataset.equals(loadSealed(tenant, dataset.manifest().artifactId())))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            return;
        }
        if (inputs.insert(converter.toInput(tenant, fileId, dataset.manifest())) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
        for (var line : dataset.records())
            if (entries.insert(converter.toEntry(tenant, dataset.manifest().artifactId(), line)) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
    }

    private ReconciliationManifest manifest(ReconciliationInputDO row) {
        try {
            if (row.getPaymentCount() == null || row.getPaymentAmountMinor() == null || row.getRefundCount() == null
                    || row.getRefundAmountMinor() == null || row.getRejectionCode() != null)
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.toManifest(row);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper key(String tenant, String id) {
        io.github.loadup.commons.validation.DomainChecks.id(id);
        return QueryWrapper.create().where(RECONCILIATION_INPUT_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_INPUT_DO.DELETED.eq(0))
                .and(RECONCILIATION_INPUT_DO.ID.eq(id));
    }
}
