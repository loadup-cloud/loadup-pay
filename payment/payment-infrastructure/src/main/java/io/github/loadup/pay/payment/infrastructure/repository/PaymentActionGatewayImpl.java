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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.codec.ChannelActionCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentActionGateway;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAction;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentActionStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentActionDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentActionDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ACTION_DO;

@Repository
public class PaymentActionGatewayImpl implements PaymentActionGateway {
    private static final int MAX_PAYLOAD_LENGTH = 8192;

    private final PaymentActionDOMapper mapper;
    private final PaymentActionStorageConverter converter;
    private final ChannelActionCodec codec;

    public PaymentActionGatewayImpl(PaymentActionDOMapper mapper, PaymentActionStorageConverter converter,
            ChannelActionCodec codec) {
        this.mapper = mapper;
        this.converter = converter;
        this.codec = codec;
    }

    @Override
    public Optional<StoredPaymentAction> find(String tenant, String attemptId) {
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(PAYMENT_ACTION_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_ACTION_DO.DELETED.eq(0)).and(PAYMENT_ACTION_DO.ID.eq(attemptId)));
        if (row == null) return Optional.empty();
        try {
            if (row.getActionPayload() == null || row.getActionPayload().length() > MAX_PAYLOAD_LENGTH
                    || row.getIssuedAt() == null || row.getExpiresAt() == null
                    || !row.getExpiresAt().isAfter(row.getIssuedAt()))
                throw new CommonException(SNAPSHOT_INVALID);
            ChannelPaymentAction action = codec.decode(row.getActionKind(), row.getActionPayload(), row.getPayloadHash());
            if (action == null || !BusinessTime.local(action.expiresAt()).equals(row.getExpiresAt()))
                throw new CommonException(SNAPSHOT_INVALID);
            var value = converter.toDomain(row, action);
            if (value.source() == io.github.loadup.pay.payment.domain.model.PaymentActionSource.LOCAL_SIGNED
                    && !value.sourceDigest().equals(row.getPayloadHash())) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredPaymentAction value) {
        writable(tenant);
        if (value == null) throw new CommonException(INVALID_ARGUMENT);
        try {
            var expiresAt = BusinessTime.local(value.action().expiresAt());
            if (!expiresAt.isAfter(value.issuedAt())) throw new CommonException(INVALID_ARGUMENT);
            String kind = codec.kind(value.action());
            String payload = codec.encode(value.action());
            if (kind == null || kind.isBlank() || payload == null || payload.length() > MAX_PAYLOAD_LENGTH)
                throw new CommonException(INVALID_ARGUMENT);
            String payloadHash = codec.checksum(payload);
            if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}"))
                throw new CommonException(INVALID_ARGUMENT);
            if (value.source() == io.github.loadup.pay.payment.domain.model.PaymentActionSource.LOCAL_SIGNED
                    && !value.sourceDigest().equals(payloadHash)) throw new CommonException(INVALID_ARGUMENT);
            PaymentActionDO row = converter.toDO(tenant, value, kind, payload, payloadHash, expiresAt);
            if (mapper.insert(row) != 1) throw new CommonException(CONCURRENT_UPDATE);
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
