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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.gateway.RefundAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.RefundAcceptanceEvidence;
import io.github.loadup.pay.payment.infrastructure.converter.RefundAcceptanceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAcceptanceDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RefundAcceptanceGatewayImpl implements RefundAcceptanceGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final RefundAcceptanceStorageConverter converter;
    private final RefundStorageConverter storage;
    private final Clock clock;

    public RefundAcceptanceGatewayImpl(NamedParameterJdbcTemplate jdbc,
                                       RefundAcceptanceStorageConverter converter, RefundStorageConverter storage,
                                       @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public boolean remember(String tenant, RefundAcceptanceEvidence value, long expectedChildVersion) {
        transaction(tenant, true);
        if (value == null || expectedChildVersion < 0) throw new CommonException(INVALID_ARGUMENT);
        var childArgs = new MapSqlParameterSource("tenant", tenant).addValue("id", value.refundId());
        var child = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, refund_request_id, payment_id, original_attempt_id,
                       binding_revision_id, channel, account_scope_hash, original_channel_request_no, original_channel_transaction_no,
                       original_succeeded_at, submission_started_at, channel_refund_no, channel_refund_transaction_no, amount_minor,
                       currency, state, first_unknown_at, terminal_evidence_id, next_query_at, query_sequence, succeeded_at, row_version FROM refund_order
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE FOR UPDATE
                """, childArgs, BeanPropertyRowMapper.newInstance(RefundOrderDO.class)).stream().findFirst().orElse(null);
        if (child == null || child.getRowVersion() == null || child.getRowVersion() != expectedChildVersion
                || child.getSubmissionStartedAt() == null || child.getOriginalSucceededAt() == null
                || value.acceptedAt().isBefore(child.getOriginalSucceededAt()) || !value.original().equals(storage.toIdentity(child))
                || !Objects.equals(value.originalChannelTransactionNo(), child.getOriginalChannelTransactionNo())
                || !Objects.equals(value.channelRefundNo(), child.getChannelRefundNo()) || !Objects.equals(child.getAmountMinor(), value.amount().getCent())
                || !Objects.equals(child.getCurrency(), value.amount().getCurrencyValue()))
            throw new CommonException(SNAPSHOT_INVALID);
        proof(tenant, value.refundId(), value.receiptId());
        var row = row(tenant, value.refundId(), true);
        if (row != null) {
            var original = read(row);
            proof(tenant, original.refundId(), original.receiptId());
            if (!sameBinding(original, value)) throw new CommonException(SNAPSHOT_INVALID);
            return original.acceptedAt().equals(value.acceptedAt());
        }
        var created = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO refund_acceptance_evidence (id, tenant_id, created_at, updated_at, deleted, payment_id, attempt_id,
                    channel, binding_revision_id, account_scope_hash, channel_request_no, original_channel_transaction_no,
                    channel_refund_no, amount_minor, currency, accepted_at, receipt_id)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :attemptId, :channel, :bindingRevisionId,
                    :accountScopeHash, :channelRequestNo, :originalChannelTransactionNo, :channelRefundNo, :amountMinor,
                    :currency, :acceptedAt, :receiptId)
                """, new BeanPropertySqlParameterSource(created));
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
        return true;
    }

    @Override
    public Optional<RefundAcceptanceEvidence> find(String tenant, String refundId) {
        transaction(tenant, false);
        var row = row(tenant, refundId, false);
        if (row == null) return Optional.empty();
        var value = read(row);
        proof(tenant, value.refundId(), value.receiptId());
        return Optional.of(value);
    }

    private RefundAcceptanceEvidence read(RefundAcceptanceDO row) {
        try {
            return converter.toModel(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private boolean sameBinding(RefundAcceptanceEvidence a, RefundAcceptanceEvidence b) {
        return a.refundId().equals(b.refundId()) && a.original().equals(b.original()) && a.originalChannelTransactionNo().equals(b.originalChannelTransactionNo())
                && a.channelRefundNo().equals(b.channelRefundNo()) && a.amount().equals(b.amount());
    }

    private void proof(String tenant, String refundId, String receiptId) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", receiptId).addValue("refund", refundId);
        var dispositions = jdbc.query("""
                SELECT disposition FROM refund_channel_receipt WHERE tenant_id = :tenant AND id = :id
                    AND refund_id = :refund AND deleted = FALSE
                """, args, (rs, index) -> rs.getString("disposition"));
        if (dispositions.size() != 1 || !"APPLIED".equals(dispositions.getFirst()) && !"IGNORED".equals(dispositions.getFirst()))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private RefundAcceptanceDO row(String tenant, String refundId, boolean lock) {
        DomainChecks.id(refundId);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", refundId);
        return jdbc.query("""
                        SELECT id, tenant_id, created_at, updated_at, payment_id, attempt_id, channel, binding_revision_id,
                               account_scope_hash, channel_request_no, original_channel_transaction_no, channel_refund_no,
                               amount_minor, currency, accepted_at, receipt_id FROM refund_acceptance_evidence
                        WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE
                        """ + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(RefundAcceptanceDO.class))
                .stream().findFirst().orElse(null);
    }

    private void transaction(String tenant, boolean write) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || write && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
