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
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.payment.app.model.PaymentVerificationTarget;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentVerificationTargetReader;
import io.github.loadup.pay.payment.app.service.PaymentExecutionBindings;
import io.github.loadup.pay.payment.client.enums.PaymentLedgerKind;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.infrastructure.converter.RefundCallbackStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentAttemptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRefundCapacityDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ATTEMPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_REFUND_CAPACITY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;

/**
 * Exact bounded local lookup. A signed channel request is built exclusively from submitted owner records.
 */
@Repository
public class PaymentVerificationTargetReaderImpl implements PaymentVerificationTargetReader {
    private final PaymentAttemptDOMapper attemptRows;
    private final RefundOrderDOMapper refundRows;
    private final PaymentRefundCapacityDOMapper capacities;
    private final PaymentAttemptGateway attempts;
    private final PaymentAcceptanceGateway payments;
    private final PaymentResultGateway claims;
    private final PaymentAcceptanceCodec commands;
    private final RefundCallbackStorageConverter refunds;

    public PaymentVerificationTargetReaderImpl(PaymentAttemptDOMapper attemptRows, RefundOrderDOMapper refundRows, PaymentRefundCapacityDOMapper capacities,
                                               PaymentAttemptGateway attempts, PaymentAcceptanceGateway payments, PaymentResultGateway claims, PaymentAcceptanceCodec commands, RefundCallbackStorageConverter refunds) {
        this.attemptRows = attemptRows;
        this.refundRows = refundRows;
        this.capacities = capacities;
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
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ).equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))
            throw new CommonException(INVALID_STATE);
        if (query.kind() == PaymentLedgerKind.PAYMENT) {
            var rows = attemptRows.selectListByQuery(QueryWrapper.create().where(PAYMENT_ATTEMPT_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ATTEMPT_DO.DELETED.eq(0))
                    .and(PAYMENT_ATTEMPT_DO.CHANNEL.eq(query.channel().name())).and(PAYMENT_ATTEMPT_DO.ACCOUNT_SCOPE_HASH.eq(query.accountScopeHash()))
                    .and(PAYMENT_ATTEMPT_DO.CHANNEL_REQUEST_NO.eq(query.merchantPaymentNo())).limit(2));
            if (rows.isEmpty()) return missing(PaymentVerificationDisposition.NO_LOCAL_TARGET);
            if (rows.size() > 1) return missing(PaymentVerificationDisposition.AMBIGUOUS_TARGET);
            var stored = attempt(tenant, rows.getFirst().getId());
            identity(query, stored);
            if (stored.attempt().executionEpoch() == 0L) return missing(PaymentVerificationDisposition.NOT_SUBMITTED);
            observable(tenant, stored);
            return new PaymentVerificationTarget(new ChannelPaymentTarget(stored.attempt().identity(), stored.attempt().amount()), null,
                    stored.rowVersion(), stored.attempt().state().name(), PaymentVerificationDisposition.OBSERVED);
        }
        var rows = refundRows.selectListByQuery(QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0))
                .and(REFUND_ORDER_DO.CHANNEL.eq(query.channel().name())).and(REFUND_ORDER_DO.ACCOUNT_SCOPE_HASH.eq(query.accountScopeHash()))
                .and(REFUND_ORDER_DO.ORIGINAL_CHANNEL_REQUEST_NO.eq(query.merchantPaymentNo())).and(REFUND_ORDER_DO.CHANNEL_REFUND_NO.eq(query.channelRefundNo())).limit(2));
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
        var capacity = capacities.selectOneByQuery(QueryWrapper.create().where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(tenant)).and(PAYMENT_REFUND_CAPACITY_DO.DELETED.eq(0))
                .and(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.eq(row.getPaymentId())));
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
