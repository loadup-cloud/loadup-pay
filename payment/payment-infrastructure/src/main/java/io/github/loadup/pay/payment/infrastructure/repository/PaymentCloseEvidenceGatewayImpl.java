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

import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.gateway.PaymentCloseEvidenceGateway;
import io.github.loadup.pay.payment.domain.model.PaymentCloseEvidence;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentCloseEvidenceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCloseEvidenceDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentCloseEvidenceMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentCloseEvidenceGatewayImpl extends ServiceImpl<PaymentCloseEvidenceMapper, PaymentCloseEvidenceDO>
        implements PaymentCloseEvidenceGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentCloseEvidenceStorageConverter converter;
    private final Clock clock;

    public PaymentCloseEvidenceGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            PaymentCloseEvidenceMapper mapper,
            PaymentCloseEvidenceStorageConverter converter,
            Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentCloseEvidence> find(String tenant, String attemptId) {
        tenant(tenant);
        DomainChecks.id(attemptId);
        var row = getById(attemptId);
        if (row == null) {
            return Optional.empty();
        }
        try {
            if (!attemptId.equals(row.getAttemptId()) || row.getAmountMinor() == null || row.getExecutionEpoch() == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentCloseEvidence value) {
        tenant(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        if (value == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_close_evidence (id, created_at, updated_at, deleted, payment_id, attempt_id,
                    channel, binding_revision_id, account_scope_hash, channel_request_no, amount_minor, currency,
                    channel_transaction_no, receipt_key, source_digest, execution_epoch, recorded_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :paymentId, :attemptId, :channel, :bindingRevisionId,
                    :accountScopeHash, :channelRequestNo, :amountMinor, :currency, :channelTransactionNo, :receiptKey,
                    :sourceDigest, :executionEpoch, :recordedAt)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void tenant(String tenant) {
        DomainChecks.id(tenant);
        if (!tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }
}
