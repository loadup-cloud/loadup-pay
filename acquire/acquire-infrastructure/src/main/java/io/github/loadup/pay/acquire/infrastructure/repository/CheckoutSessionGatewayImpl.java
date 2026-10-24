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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.infrastructure.converter.CheckoutStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.CheckoutSessionDO;
import io.github.loadup.pay.acquire.infrastructure.mapper.CheckoutSessionDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.CHECKOUT_SESSION_DO;

@Repository
public class CheckoutSessionGatewayImpl implements CheckoutSessionGateway {
    private final CheckoutSessionDOMapper mapper;
    private final CheckoutStorageConverter converter;
    private final JsonMapper json;

    public CheckoutSessionGatewayImpl(
            CheckoutSessionDOMapper mapper, CheckoutStorageConverter converter, JsonMapper json) {
        this.mapper = mapper;
        this.converter = converter;
        this.json = json.rebuild()
                .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    private QueryWrapper scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return QueryWrapper.create()
                .where(CHECKOUT_SESSION_DO.TENANT_ID.eq(tenant))
                .and(CHECKOUT_SESSION_DO.DELETED.eq(0));
    }

    @Override
    public Optional<StoredCheckoutSession> find(String tenant, String id) {
        return stored(mapper.selectOneByQuery(scope(tenant).and(CHECKOUT_SESSION_DO.ID.eq(id))));
    }

    @Override
    public Optional<StoredCheckoutSession> lock(String tenant, String id) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        return stored(mapper.selectOneByQuery(scope(tenant).and(CHECKOUT_SESSION_DO.ID.eq(id)).forUpdate()));
    }

    @Override
    public Optional<StoredCheckoutSession> findRequest(String tenant, String order, String key) {
        return stored(mapper.selectOneByQuery(scope(tenant)
                .and(CHECKOUT_SESSION_DO.ORDER_ID.eq(order))
                .and(CHECKOUT_SESSION_DO.REQUEST_KEY.eq(key))));
    }

    private Optional<StoredCheckoutSession> stored(CheckoutSessionDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getScopeSnapshot() == null || row.getScopeSnapshot().length() > 8192)
                throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(
                    converter.toStored(row, json.readValue(row.getScopeSnapshot(), CheckoutSessionScope.class)));
        } catch (RuntimeException corrupted) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredCheckoutSession value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        var payload = json.writeValueAsString(value.scope());
        if (payload.length() > 8192) throw new CommonException(SNAPSHOT_INVALID);
        if (mapper.insert(converter.toDO(tenant, value, payload)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void revoke(String tenant, StoredCheckoutSession before, StoredCheckoutSession after) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (before == null || after == null || before.revokedAt() != null || after.revokedAt() == null
                || !before.revoke(before.rowVersion(), after.revokedAt()).equals(after)) throw new CommonException(SNAPSHOT_INVALID);
        int changed = mapper.updateByQuery(converter.toRevocationPatch(after), true, scope(tenant)
                .and(CHECKOUT_SESSION_DO.ID.eq(before.session().id())).and(CHECKOUT_SESSION_DO.ROW_VERSION.eq(before.rowVersion()))
                .and(CHECKOUT_SESSION_DO.REVOKED_AT.isNull()));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
