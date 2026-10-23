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
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.PaymentChannelReceipt;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.PaymentResultAnomaly;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.mapper.ChannelTransactionClaimDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentChannelReceiptDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRefundCapacityDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentResultAnomalyDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.CHANNEL_TRANSACTION_CLAIM_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_CHANNEL_RECEIPT_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_REFUND_CAPACITY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_RESULT_ANOMALY_DO;

@Repository
public class PaymentResultGatewayImpl implements PaymentResultGateway {
    private final PaymentChannelReceiptDOMapper receipts;
    private final PaymentResultAnomalyDOMapper anomalies;
    private final ChannelTransactionClaimDOMapper claims;
    private final PaymentRefundCapacityDOMapper capacities;
    private final PaymentOrderDOMapper payments;
    private final PaymentResultStorageConverter converter;

    public PaymentResultGatewayImpl(PaymentChannelReceiptDOMapper receipts, PaymentResultAnomalyDOMapper anomalies,
                                    ChannelTransactionClaimDOMapper claims, PaymentRefundCapacityDOMapper capacities, PaymentOrderDOMapper payments,
                                    PaymentResultStorageConverter converter) {
        this.receipts = receipts;
        this.anomalies = anomalies;
        this.claims = claims;
        this.capacities = capacities;
        this.converter = converter;
        this.payments = payments;
    }

    @Override
    public Optional<PaymentChannelReceipt> receipt(String tenant, String attempt, PaymentObservation.Source source, String key) {
        var row = receipts.selectOneByQuery(QueryWrapper.create().where(PAYMENT_CHANNEL_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_CHANNEL_RECEIPT_DO.DELETED.eq(0)).and(PAYMENT_CHANNEL_RECEIPT_DO.ATTEMPT_ID.eq(attempt))
                .and(PAYMENT_CHANNEL_RECEIPT_DO.SOURCE.eq(source.name())).and(PAYMENT_CHANNEL_RECEIPT_DO.RECEIPT_KEY.eq(key)));
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toReceipt(row, converter.toIdentity(row))));
    }

    @Override
    public void insertReceipt(String tenant, PaymentChannelReceipt receipt) {
        writable(tenant);
        if (receipts.insert(converter.toReceiptDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void recordAnomaly(String tenant, PaymentResultAnomaly anomaly) {
        writable(tenant);
        var query = QueryWrapper.create().where(PAYMENT_RESULT_ANOMALY_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_RESULT_ANOMALY_DO.DELETED.eq(0)).and(PAYMENT_RESULT_ANOMALY_DO.RECEIPT_ID.eq(anomaly.receiptId()))
                .and(PAYMENT_RESULT_ANOMALY_DO.FACTS_DIGEST.eq(anomaly.factsDigest()))
                .and(PAYMENT_RESULT_ANOMALY_DO.REASON_CODE.eq(anomaly.reasonCode()));
        if (anomalies.selectOneByQuery(query) != null) return;
        if (anomalies.insert(converter.toAnomalyDO(tenant, anomaly)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<ChannelTransactionClaim> transaction(Channel channel, String scope, String number) {
        // This one exact-key immutable lookup must see financial aliases in other tenants.
        // No collection/list query or outward-facing API bypasses tenant isolation.
        var row = TenantManager.withoutTenantCondition(() -> claims.selectOneByQuery(QueryWrapper.create()
                .where(CHANNEL_TRANSACTION_CLAIM_DO.CHANNEL.eq(channel.name()))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.ACCOUNT_SCOPE_HASH.eq(scope))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.CHANNEL_TRANSACTION_NO.eq(number))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.DELETED.eq(0))));
        return claim(row);
    }

    @Override
    public Optional<ChannelTransactionClaim> attemptClaim(String tenant, String attempt) {
        return claim(claims.selectOneByQuery(QueryWrapper.create().where(CHANNEL_TRANSACTION_CLAIM_DO.TENANT_ID.eq(tenant))
                .and(CHANNEL_TRANSACTION_CLAIM_DO.DELETED.eq(0)).and(CHANNEL_TRANSACTION_CLAIM_DO.ATTEMPT_ID.eq(attempt))));
    }

    private Optional<ChannelTransactionClaim> claim(ChannelTransactionClaimDO row) {
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toClaim(row, converter.toIdentity(row))));
    }

    @Override
    public void insertClaim(ChannelTransactionClaim claim) {
        writable(claim.tenantId());
        if (claims.insert(converter.toClaimDO(claim)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void completeSuccess(String tenant, StoredPaymentAcceptance payment, long expectedVersion, java.time.LocalDateTime succeededAt) {
        writable(tenant);
        if (payment.payment().state() != PaymentState.SUCCEEDED || payment.terminalEvidenceId() == null || succeededAt == null
                || payment.payment().version() != Math.incrementExact(expectedVersion))
            throw new CommonException(INVALID_STATE);
        int changed = payments.updateByQuery(converter.toSuccessPatch(payment, succeededAt), true,
                QueryWrapper.create().where(PAYMENT_ORDER_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ORDER_DO.DELETED.eq(0))
                        .and(PAYMENT_ORDER_DO.ID.eq(payment.payment().id()))
                        .and(PAYMENT_ORDER_DO.ACTIVE_ATTEMPT_ID.eq(payment.payment().successfulAttemptId()))
                        .and(PAYMENT_ORDER_DO.STATE.in("PROCESSING", "UNKNOWN", "CLOSING"))
                        .and(PAYMENT_ORDER_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<PaymentRefundCapacity> capacity(String tenant, String payment) {
        var row = capacities.selectOneByQuery(QueryWrapper.create().where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_REFUND_CAPACITY_DO.DELETED.eq(0)).and(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.eq(payment)));
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toCapacity(row)));
    }

    @Override
    public void insertCapacity(String tenant, String id, PaymentRefundCapacity capacity) {
        writable(tenant);
        if (capacity.version() != 0 || capacity.reservedMinor() != 0 || capacity.refundedMinor() != 0)
            throw new CommonException(INVALID_STATE);
        if (capacities.insert(converter.toCapacityDO(tenant, id, capacity)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private <T> T read(Supplier<T> mapping) {
        try {
            return mapping.get();
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
