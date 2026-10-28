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
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentAttemptStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentAttemptDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentAttemptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ATTEMPT_DO;

@Repository
public class PaymentAttemptGatewayImpl implements PaymentAttemptGateway {
    private final PaymentAttemptDOMapper attempts;
    private final ChannelBindingGateway bindings;
    private final PaymentAttemptStorageConverter converter;

    public PaymentAttemptGatewayImpl(PaymentAttemptDOMapper attempts, ChannelBindingGateway bindings,
                                     PaymentAttemptStorageConverter converter) {
        this.attempts = attempts;
        this.bindings = bindings;
        this.converter = converter;
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(PAYMENT_ATTEMPT_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ATTEMPT_DO.DELETED.eq(0));
    }

    @Override
    public Optional<StoredPaymentAttempt> find(String tenant, String id) {
        return read(tenant, attempts.selectOneByQuery(scope(tenant).and(PAYMENT_ATTEMPT_DO.ID.eq(id))));
    }

    @Override
    public Optional<StoredPaymentAttempt> findRequest(String tenant, String payment, String key) {
        return read(tenant, attempts.selectOneByQuery(scope(tenant).and(PAYMENT_ATTEMPT_DO.PAYMENT_ID.eq(payment))
                .and(PAYMENT_ATTEMPT_DO.REQUEST_KEY.eq(key))));
    }

    @Override
    public Optional<StoredPaymentAttempt> findChannelRequest(String tenant, String bindingRevisionId,
                                                             String channelRequestNo) {
        var rows = attempts.selectListByQuery(scope(tenant)
                .and(PAYMENT_ATTEMPT_DO.BINDING_REVISION_ID.eq(bindingRevisionId))
                .and(PAYMENT_ATTEMPT_DO.CHANNEL_REQUEST_NO.eq(channelRequestNo)).limit(2));
        if (rows.size() > 1) throw new CommonException(SNAPSHOT_INVALID);
        return rows.isEmpty() ? Optional.empty() : read(tenant, rows.get(0));
    }

    @Override
    public Optional<StoredPaymentAttempt> lock(String tenant, String id) {
        requireTransaction();
        return read(tenant, attempts.selectOneByQuery(scope(tenant).and(PAYMENT_ATTEMPT_DO.ID.eq(id)).forUpdate()));
    }

    private Optional<StoredPaymentAttempt> read(String tenant, PaymentAttemptDO row) {
        if (row == null) return Optional.empty();
        try {
            var binding = bindings.findRevision(tenant, row.getBindingRevisionId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!binding.channel().name().equals(row.getChannel()) || !binding.accessMode().name().equals(row.getAccessMode())
                    || !binding.accountScopeHash().equals(row.getAccountScopeHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            var risk = row.getRiskDecision() == null ? null : converter.toRisk(row);
            if (risk == null && (row.getRiskReason() != null || row.getRiskConsultationRef() != null))
                throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toStored(row, converter.toAttempt(row, binding), risk));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredPaymentAttempt value) {
        requireTransaction();
        if (value.attempt().state() != PaymentAttemptState.INIT || value.rowVersion() != 0)
            throw new CommonException(INVALID_STATE);
        if (attempts.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, StoredPaymentAttempt value, long expectedVersion) {
        requireTransaction();
        if (value.rowVersion() != Math.incrementExact(expectedVersion)) throw new CommonException(INVALID_STATE);
        int changed = attempts.updateByQuery(converter.toTransitionPatch(value), true,
                scope(tenant).and(PAYMENT_ATTEMPT_DO.ID.eq(value.attempt().id()))
                        .and(PAYMENT_ATTEMPT_DO.PAYMENT_ID.eq(value.attempt().paymentId()))
                        .and(PAYMENT_ATTEMPT_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
