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
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionRevocationGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionRevocationReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.CheckoutSessionRevocationReceiptStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.CheckoutSessionRevocationReceiptDO;
import io.github.loadup.pay.acquire.infrastructure.mapper.CheckoutSessionRevocationReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.CHECKOUT_SESSION_REVOCATION_RECEIPT_DO;

@Repository
public class CheckoutSessionRevocationGatewayImpl implements CheckoutSessionRevocationGateway {
    private final CheckoutSessionRevocationReceiptDOMapper mapper;
    private final CheckoutSessionRevocationReceiptStorageConverter converter;
    public CheckoutSessionRevocationGatewayImpl(CheckoutSessionRevocationReceiptDOMapper mapper, CheckoutSessionRevocationReceiptStorageConverter converter) {
        this.mapper = mapper; this.converter = converter;
    }
    @Override public Optional<CheckoutSessionRevocationReceipt> find(String tenant, String callerScopeHash, String requestKey) {
        var row = mapper.selectOneByQuery(scope(tenant).and(CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.CALLER_SCOPE_HASH.eq(callerScopeHash))
                .and(CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.REQUEST_KEY.eq(requestKey)));
        return Optional.ofNullable(row).map(this::decode);
    }
    @Override public void insert(String tenant, CheckoutSessionRevocationReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
    @Override public PageResult<CheckoutSessionRevocationReceipt> page(String tenant, String sessionId, int page, int size) {
        PageQuery.normalized(page, size, INVALID_PAGE);
        var query = scope(tenant).and(CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.SESSION_ID.eq(sessionId));
        long total = mapper.selectCountByQuery(query);
        query.orderBy(CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.ACCEPTED_AT.desc(), CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.ID.desc())
                .limit(size).offset((long) (page - 1) * size);
        return PageResult.of(mapper.selectListByQuery(query).stream().map(this::decode).toList(), total, page, size);
    }
    private QueryWrapper scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return QueryWrapper.create().where(CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(CHECKOUT_SESSION_REVOCATION_RECEIPT_DO.DELETED.eq(0));
    }
    private CheckoutSessionRevocationReceipt decode(CheckoutSessionRevocationReceiptDO row) {
        try { return converter.toDomain(row); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
}
