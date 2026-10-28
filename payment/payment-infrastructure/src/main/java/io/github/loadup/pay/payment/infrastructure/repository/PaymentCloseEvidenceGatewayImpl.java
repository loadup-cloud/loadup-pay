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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.gateway.PaymentCloseEvidenceGateway;
import io.github.loadup.pay.payment.domain.model.PaymentCloseEvidence;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentCloseEvidenceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentCloseEvidenceDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_CLOSE_EVIDENCE_DO;

@Repository
public class PaymentCloseEvidenceGatewayImpl implements PaymentCloseEvidenceGateway {
    private final PaymentCloseEvidenceDOMapper mapper;
    private final PaymentCloseEvidenceStorageConverter converter;

    public PaymentCloseEvidenceGatewayImpl(PaymentCloseEvidenceDOMapper mapper, PaymentCloseEvidenceStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<PaymentCloseEvidence> find(String tenant, String attemptId) {
        tenant(tenant);
        DomainChecks.id(attemptId);
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(PAYMENT_CLOSE_EVIDENCE_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_CLOSE_EVIDENCE_DO.ID.eq(attemptId)).and(PAYMENT_CLOSE_EVIDENCE_DO.DELETED.eq(0)));
        if (row == null) return Optional.empty();
        try {
            if (!attemptId.equals(row.getAttemptId()) || row.getAmountMinor() == null || row.getExecutionEpoch() == null)
                throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentCloseEvidence value) {
        tenant(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (value == null) throw new CommonException(INVALID_ARGUMENT);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void tenant(String tenant) {
        DomainChecks.id(tenant);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
