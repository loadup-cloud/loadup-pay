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
import com.mybatisflex.core.tenant.TenantManager;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.RefundChannelReceipt;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundResultAnomaly;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.infrastructure.converter.RefundResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRefundCapacityDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundAllocationPlanDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundChannelReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundRequestDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundResultAnomalyDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundTransactionClaimDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_REFUND_CAPACITY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ALLOCATION_PLAN_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_CHANNEL_RECEIPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_REQUEST_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_RESULT_ANOMALY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_TRANSACTION_CLAIM_DO;

@Repository
public class RefundResultGatewayImpl implements RefundResultGateway {
    private final RefundChannelReceiptDOMapper receipts;
    private final RefundResultAnomalyDOMapper anomalies;
    private final RefundTransactionClaimDOMapper claims;
    private final RefundOrderDOMapper children;
    private final RefundRequestDOMapper requests;
    private final RefundAllocationPlanDOMapper plans;
    private final PaymentRefundCapacityDOMapper capacities;
    private final RefundResultStorageConverter converter;
    private final RefundStorageConverter storage;
    private final Clock clock;

    public RefundResultGatewayImpl(RefundChannelReceiptDOMapper receipts, RefundResultAnomalyDOMapper anomalies,
                                   RefundTransactionClaimDOMapper claims, RefundOrderDOMapper children, RefundRequestDOMapper requests,
                                   RefundAllocationPlanDOMapper plans, PaymentRefundCapacityDOMapper capacities,
                                   RefundResultStorageConverter converter, RefundStorageConverter storage,
                                   @Qualifier("paymentClock") Clock clock) {
        this.receipts = receipts;
        this.anomalies = anomalies;
        this.claims = claims;
        this.children = children;
        this.requests = requests;
        this.plans = plans;
        this.capacities = capacities;
        this.converter = converter;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public Optional<String> requestId(String tenant, String refundId) {
        scoped(tenant);
        var row = children.selectOneByQuery(childScope(tenant).and(REFUND_ORDER_DO.ID.eq(refundId)));
        return Optional.ofNullable(row).map(RefundOrderDO::getRefundRequestId);
    }

    @Override
    public RefundExecutionFacts lockChild(String tenant, String refundId) {
        writable(tenant);
        var row = children.selectOneByQuery(childScope(tenant).and(REFUND_ORDER_DO.ID.eq(refundId)).forUpdate());
        if (row == null) throw new CommonException(NOT_FOUND);
        var plan = plans.selectOneByQuery(QueryWrapper.create().where(REFUND_ALLOCATION_PLAN_DO.TENANT_ID.eq(tenant))
                .and(REFUND_ALLOCATION_PLAN_DO.DELETED.eq(0)).and(REFUND_ALLOCATION_PLAN_DO.REFUND_ID.eq(refundId)));
        return read(() -> {
            if (plan == null || !plan.getRefundRequestId().equals(row.getRefundRequestId()) || !plan.getPaymentId().equals(row.getPaymentId())
                    || !plan.getAmountMinor().equals(row.getAmountMinor()) || plan.getPlanVersion() != 1)
                throw new CommonException(SNAPSHOT_INVALID);
            var child = storage.toChild(row, storage.toRefund(row), storage.toIdentity(row), plan.getSequenceNo());
            return converter.toFacts(row, child);
        });
    }

    @Override
    public Optional<RefundChannelReceipt> receipt(String tenant, String refundId, RefundObservation.Source source, String key) {
        scoped(tenant);
        var row = receipts.selectOneByQuery(QueryWrapper.create().where(REFUND_CHANNEL_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(REFUND_CHANNEL_RECEIPT_DO.DELETED.eq(0)).and(REFUND_CHANNEL_RECEIPT_DO.REFUND_ID.eq(refundId))
                .and(REFUND_CHANNEL_RECEIPT_DO.SOURCE.eq(source.name())).and(REFUND_CHANNEL_RECEIPT_DO.RECEIPT_KEY.eq(key)));
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toReceipt(row)));
    }

    @Override
    public void insertReceipt(String tenant, RefundChannelReceipt receipt) {
        writable(tenant);
        if (receipts.insert(converter.toReceiptDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void recordAnomaly(String tenant, RefundResultAnomaly value) {
        writable(tenant);
        var existing = anomalies.selectOneByQuery(QueryWrapper.create().where(REFUND_RESULT_ANOMALY_DO.TENANT_ID.eq(tenant))
                .and(REFUND_RESULT_ANOMALY_DO.DELETED.eq(0)).and(REFUND_RESULT_ANOMALY_DO.RECEIPT_ID.eq(value.receiptId()))
                .and(REFUND_RESULT_ANOMALY_DO.FACTS_DIGEST.eq(value.factsDigest())).and(REFUND_RESULT_ANOMALY_DO.REASON_CODE.eq(value.reasonCode())));
        if (existing == null && anomalies.insert(converter.toAnomalyDO(tenant, value)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<RefundTransactionClaim> transaction(Channel channel, String scope, String number) {
        writable(TenantUtil.getTenantId());
        // One exact immutable financial key must see aliases across tenants. Never expose this lookup as an API.
        return claim(TenantManager.withoutTenantCondition(() -> claims.selectOneByQuery(QueryWrapper.create()
                .where(REFUND_TRANSACTION_CLAIM_DO.CHANNEL.eq(channel.name())).and(REFUND_TRANSACTION_CLAIM_DO.ACCOUNT_SCOPE_HASH.eq(scope))
                .and(REFUND_TRANSACTION_CLAIM_DO.CHANNEL_REFUND_TRANSACTION_NO.eq(number)).and(REFUND_TRANSACTION_CLAIM_DO.DELETED.eq(0)))));
    }

    @Override
    public Optional<RefundTransactionClaim> claim(String tenant, String refundId) {
        scoped(tenant);
        return claim(claims.selectOneByQuery(QueryWrapper.create().where(REFUND_TRANSACTION_CLAIM_DO.TENANT_ID.eq(tenant))
                .and(REFUND_TRANSACTION_CLAIM_DO.DELETED.eq(0)).and(REFUND_TRANSACTION_CLAIM_DO.REFUND_ID.eq(refundId))));
    }

    private Optional<RefundTransactionClaim> claim(RefundTransactionClaimDO row) {
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toClaim(row, converter.toIdentity(row))));
    }

    @Override
    public void insertClaim(RefundTransactionClaim value) {
        writable(value.tenantId());
        if (claims.insert(converter.toClaimDO(value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, RefundProgressChange change) {
        writable(tenant);
        var before = change.before().child().refund();
        var childQuery = childScope(tenant).and(REFUND_ORDER_DO.ID.eq(before.id()))
                .and(REFUND_ORDER_DO.REFUND_REQUEST_ID.eq(before.refundRequestId())).and(REFUND_ORDER_DO.PAYMENT_ID.eq(before.paymentId()))
                .and(REFUND_ORDER_DO.STATE.eq(before.state().name())).and(REFUND_ORDER_DO.ROW_VERSION.eq(before.version()));
        if (change.before().submissionStartedAt() == null)
            childQuery.and(REFUND_ORDER_DO.SUBMISSION_STARTED_AT.isNull());
        else childQuery.and(REFUND_ORDER_DO.SUBMISSION_STARTED_AT.eq(change.before().submissionStartedAt()));
        LocalDateTime firstUnknownAt = null;
        if (change.refund().state() == RefundState.UNKNOWN) {
            // Preserve the first observation across later UNKNOWN results and recovery generations.
            var row = children.selectOneByQuery(childScope(tenant)
                    .select(REFUND_ORDER_DO.ID, REFUND_ORDER_DO.FIRST_UNKNOWN_AT)
                    .and(REFUND_ORDER_DO.ID.eq(before.id()))
                    .and(REFUND_ORDER_DO.ROW_VERSION.eq(before.version())));
            if (row == null) throw new CommonException(CONCURRENT_UPDATE);
            firstUnknownAt = row.getFirstUnknownAt() == null
                    ? LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS) : row.getFirstUnknownAt();
        }
        if (children.updateByQuery(converter.toChildPatch(change, firstUnknownAt), true, childQuery) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
        if (requests.updateByQuery(converter.toRequestPatch(change.request()), true,
                QueryWrapper.create().where(REFUND_REQUEST_DO.TENANT_ID.eq(tenant)).and(REFUND_REQUEST_DO.DELETED.eq(0))
                        .and(REFUND_REQUEST_DO.ID.eq(change.beforeRequest().id())).and(REFUND_REQUEST_DO.ROW_VERSION.eq(change.beforeRequest().version()))
                        .and(REFUND_REQUEST_DO.STATE.eq(change.beforeRequest().state().name()))) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
        if (!change.capacity().equals(change.beforeCapacity())) {
            var capacity = change.beforeCapacity();
            if (capacities.updateByQuery(converter.toCapacityPatch(change.capacity()), true,
                    QueryWrapper.create().where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(tenant)).and(PAYMENT_REFUND_CAPACITY_DO.DELETED.eq(0))
                            .and(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.eq(capacity.paymentId())).and(PAYMENT_REFUND_CAPACITY_DO.ORDER_ID.eq(capacity.orderId()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.PAYEE_MERCHANT_ID.eq(capacity.payeeMerchantId()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.ROW_VERSION.eq(capacity.version())).and(PAYMENT_REFUND_CAPACITY_DO.RESERVED_MINOR.eq(capacity.reservedMinor()))
                            .and(PAYMENT_REFUND_CAPACITY_DO.REFUNDED_MINOR.eq(capacity.refundedMinor()))) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private QueryWrapper childScope(String tenant) {
        return QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(tenant)).and(REFUND_ORDER_DO.DELETED.eq(0));
    }

    private void scoped(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void writable(String tenant) {
        scoped(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }

    private <T> T read(Supplier<T> action) {
        try {
            return action.get();
        } catch (CommonException | org.springframework.dao.DataAccessException failure) {
            throw failure;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
