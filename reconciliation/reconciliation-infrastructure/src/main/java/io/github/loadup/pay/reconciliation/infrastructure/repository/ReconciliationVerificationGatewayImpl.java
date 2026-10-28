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
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationVerificationGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationState;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationVerificationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationVerificationDO;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationVerificationDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.RECONCILIATION_VERIFICATION_DO;

@Repository
public class ReconciliationVerificationGatewayImpl implements ReconciliationVerificationGateway {
    private final ReconciliationVerificationDOMapper mapper;
    private final ReconciliationVerificationStorageConverter converter;

    public ReconciliationVerificationGatewayImpl(ReconciliationVerificationDOMapper mapper, ReconciliationVerificationStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationVerificationRequest> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        return read(mapper.selectOneByQuery(scope(tenant).and(RECONCILIATION_VERIFICATION_DO.CALLER_SERVICE_ID.eq(caller))
                .and(RECONCILIATION_VERIFICATION_DO.REQUEST_KEY.eq(requestKey))));
    }

    @Override
    public Optional<ReconciliationVerificationRequest> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        return read(mapper.selectOneByQuery(key(tenant, id)));
    }

    @Override
    public Optional<ReconciliationVerificationRequest> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        return read(mapper.selectOneByQuery(key(tenant, id).forUpdate()));
    }

    @Override
    public void insert(String tenant, ReconciliationVerificationRequest value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.state() != ReconciliationVerificationState.INIT || value.version() != 0L)
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, ReconciliationVerificationRequest value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE || value.version() != expectedVersion + 1L)
            throw new CommonException(INVALID_STATE);
        var original = lock(tenant, value.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (original.version() != expectedVersion) throw new CommonException(CONCURRENT_UPDATE);
        if (!converter.toInitial(original).equals(converter.toInitial(value)) || original.state() != ReconciliationVerificationState.INIT
                || value.state() == ReconciliationVerificationState.INIT) throw new CommonException(SNAPSHOT_INVALID);
        if (mapper.updateByQuery(converter.toDO(tenant, value), true, key(tenant, value.id())
                .and(RECONCILIATION_VERIFICATION_DO.ROW_VERSION.eq(expectedVersion))) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private Optional<ReconciliationVerificationRequest> read(ReconciliationVerificationDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null || row.getReviewVersion() == null)
                throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper key(String tenant, String id) {
        DomainChecks.id(id);
        return scope(tenant).and(RECONCILIATION_VERIFICATION_DO.ID.eq(id));
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(RECONCILIATION_VERIFICATION_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_VERIFICATION_DO.DELETED.eq(0));
    }
}
