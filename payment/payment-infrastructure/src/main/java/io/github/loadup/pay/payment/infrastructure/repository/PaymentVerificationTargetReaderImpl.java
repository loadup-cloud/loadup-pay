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
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.payment.app.model.PaymentVerificationTarget;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentVerificationTargetReader;
import io.github.loadup.pay.payment.app.service.PaymentExecutionBindings;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.infrastructure.converter.RefundCallbackStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Exact bounded local lookup. A signed channel request is built exclusively from submitted owner records.
 */
@Repository
public class PaymentVerificationTargetReaderImpl implements PaymentVerificationTargetReader {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentAttemptGateway attempts;
    private final PaymentAcceptanceGateway payments;
    private final PaymentResultGateway claims;
    private final PaymentAcceptanceCodec commands;
    private final RefundCallbackStorageConverter refunds;

    public PaymentVerificationTargetReaderImpl(NamedParameterJdbcTemplate jdbc,
                                               PaymentAttemptGateway attempts, PaymentAcceptanceGateway payments, PaymentResultGateway claims, PaymentAcceptanceCodec commands, RefundCallbackStorageConverter refunds) {
        this.jdbc = jdbc;
        this.attempts = attempts;
        this.payments = payments;
        this.claims = claims;
        this.commands = commands;
        this.refunds = refunds;
    }

    @Override
    public PaymentVerificationTarget read(String tenant, PaymentVerificationQuery query) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (query == null || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("channel", query.channel().name())
                .addValue("scope", query.accountScopeHash()).addValue("paymentNo", query.merchantPaymentNo());
        if (query.kind() == PaymentLedgerKind.PAYMENT) {
            var rows = jdbc.query("""
                    SELECT id FROM payment_attempt WHERE tenant_id = :tenant AND deleted = FALSE AND channel = :channel
                        AND account_scope_hash = :scope AND channel_request_no = :paymentNo LIMIT 2
                    """, args, (rs, index) -> rs.getString("id"));
            if (rows.isEmpty()) return missing(PaymentVerificationDisposition.NO_LOCAL_TARGET);
            if (rows.size() > 1) return missing(PaymentVerificationDisposition.AMBIGUOUS_TARGET);
            var stored = attempt(tenant, rows.getFirst());
            identity(query, stored);
            if (stored.attempt().executionEpoch() == 0L) return missing(PaymentVerificationDisposition.NOT_SUBMITTED);
            observable(tenant, stored);
            return new PaymentVerificationTarget(new ChannelPaymentTarget(stored.attempt().identity(), stored.attempt().amount()), null,
                    stored.rowVersion(), stored.attempt().state().name(), PaymentVerificationDisposition.OBSERVED);
        }
        args.addValue("refundNo", query.channelRefundNo());
        var rows = jdbc.query("""
                SELECT id, payment_id, original_attempt_id, binding_revision_id, channel, account_scope_hash,
                       original_channel_request_no, original_channel_transaction_no, original_succeeded_at,
                       submission_started_at, channel_refund_no, amount_minor, currency, state, row_version
                FROM refund_order WHERE tenant_id = :tenant AND deleted = FALSE AND channel = :channel
                    AND account_scope_hash = :scope AND original_channel_request_no = :paymentNo
                    AND channel_refund_no = :refundNo LIMIT 2
                """, args, BeanPropertyRowMapper.newInstance(RefundOrderDO.class));
        if (rows.isEmpty()) return missing(PaymentVerificationDisposition.NO_LOCAL_TARGET);
        if (rows.size() > 1) return missing(PaymentVerificationDisposition.AMBIGUOUS_TARGET);
        var row = rows.getFirst();
        if (row.getRowVersion() == null || row.getRowVersion() < 0 || row.getState() == null)
            throw new CommonException(SNAPSHOT_INVALID);
        if (row.getSubmissionStartedAt() == null) {
            if (!"INIT".equals(row.getState())) throw new CommonException(SNAPSHOT_INVALID);
            return missing(PaymentVerificationDisposition.NOT_SUBMITTED);
        }
        if ("INIT".equals(row.getState())) throw new CommonException(SNAPSHOT_INVALID);
        var original = attempt(tenant, row.getOriginalAttemptId());
        identity(query, original);
        observable(tenant, original);
        if (!original.attempt().identity().equals(refunds.toIdentity(row))) throw new CommonException(SNAPSHOT_INVALID);
        var claim = claims.attemptClaim(tenant, original.attempt().id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var capacityArgs = new MapSqlParameterSource("tenant", tenant).addValue("payment", row.getPaymentId());
        var capacity = jdbc.query("""
                SELECT id, payment_id, succeeded_at, succeeded_minor, currency FROM payment_refund_capacity
                WHERE tenant_id = :tenant AND deleted = FALSE AND payment_id = :payment
                """, capacityArgs, BeanPropertyRowMapper.newInstance(PaymentRefundCapacityDO.class)).stream().findFirst().orElse(null);
        if (capacity == null || !tenant.equals(claim.tenantId()) || !claim.identity().equals(original.attempt().identity())
                || !claim.amount().equals(original.attempt().amount()) || !claim.channelTransactionNo().equals(row.getOriginalChannelTransactionNo())
                || !claim.succeededAt().equals(row.getOriginalSucceededAt()) || !claim.succeededAt().equals(capacity.getSucceededAt())
                || capacity.getSucceededMinor() == null || capacity.getSucceededMinor() != claim.amount().getCent() || !Objects.equals(capacity.getCurrency(), row.getCurrency()))
            throw new CommonException(SNAPSHOT_INVALID);
        return new PaymentVerificationTarget(null, refunds.toPrepared(row, capacity, original.attempt().identity()), row.getRowVersion(), row.getState(), PaymentVerificationDisposition.OBSERVED);
    }

    private StoredPaymentAttempt attempt(String tenant, String id) {
        return attempts.find(tenant, id).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
    }

    private void observable(String tenant, StoredPaymentAttempt stored) {
        var accepted = payments.find(tenant, stored.attempt().paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        commands.decode(accepted);
        PaymentExecutionBindings.observable(accepted, stored);
    }

    private void identity(PaymentVerificationQuery query, StoredPaymentAttempt stored) {
        var original = stored.attempt().identity();
        if (query.channel() != original.channel() || !query.accountScopeHash().equals(original.accountScopeHash()) || !query.merchantPaymentNo().equals(original.channelRequestNo()))
            throw new CommonException(SNAPSHOT_INVALID);
    }

    private PaymentVerificationTarget missing(PaymentVerificationDisposition disposition) {
        return new PaymentVerificationTarget(null, null, null, null, disposition);
    }
}
