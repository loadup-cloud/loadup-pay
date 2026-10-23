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
package io.github.loadup.pay.realtime.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.realtime.app.port.TradeProjectionCodec;
import io.github.loadup.pay.realtime.domain.gateway.*;
import io.github.loadup.pay.realtime.domain.model.*;
import io.github.loadup.pay.realtime.infrastructure.converter.TradeStorageConverter;
import io.github.loadup.pay.realtime.infrastructure.dataobject.*;
import io.github.loadup.pay.realtime.infrastructure.mapper.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static io.github.loadup.pay.realtime.infrastructure.dataobject.table.Tables.*;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.*;

@Repository
public class TradeProjectionGatewayImpl implements TradeProjectionGateway, TradeQueryGateway {
    private final TradeOrderDOMapper orders;
    private final TradePaymentDOMapper payments;
    private final TradeRefundDOMapper refunds;
    private final TradeSummaryDOMapper summaries;
    private final TradeStorageConverter converter;
    private final TradeProjectionCodec codec;
    private final IdGenerator ids;
    public TradeProjectionGatewayImpl(TradeOrderDOMapper orders, TradePaymentDOMapper payments, TradeRefundDOMapper refunds, TradeSummaryDOMapper summaries,
            TradeStorageConverter converter, TradeProjectionCodec codec, IdGenerator ids) {
        this.orders = orders;
        this.payments = payments;
        this.refunds = refunds;
        this.summaries = summaries;
        this.converter = converter; this.codec = codec; this.ids = ids;
    }

    private QueryWrapper orderScope(String tenant) {
        return QueryWrapper.create().where(TRADE_ORDER_DO.TENANT_ID.eq(tenant)).and(TRADE_ORDER_DO.DELETED.eq(0));
    }
    private QueryWrapper orderKey(String tenant, String key) {
        return orderScope(tenant).and(TRADE_ORDER_DO.ORDER_ID.eq(key));
    }
    private TradeOrderProjection read(TradeOrderDO row) {
        try {
            var value = codec.order(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId())
                    || !row.getMerchantOrderNo().equals(value.merchantOrderNo())
                    || !row.getState().equals(value.state())
                    || !row.getBusinessCreatedAt().equals(value.orderCreatedAt())) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public ProjectionAdvance applyOrderIfNewer(String tenant, TradeOrderProjection value, String factsHash) {
        writable();
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = orders.selectOneByQuery(orderKey(tenant, value.orderId()).forUpdate());
        if (row == null) {
            if (orders.insert(converter.toDO(ids.generate(), tenant, value, factsHash, payload)) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number()) return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        int changed = orders.updateByQuery(converter.toPatch(value, factsHash, payload), true,
                orderKey(tenant, value.orderId()).and(TRADE_ORDER_DO.SOURCE_VERSION.eq(row.getSourceVersion())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    private QueryWrapper paymentScope(String tenant) {
        return QueryWrapper.create().where(TRADE_PAYMENT_DO.TENANT_ID.eq(tenant)).and(TRADE_PAYMENT_DO.DELETED.eq(0));
    }
    private QueryWrapper paymentKey(String tenant, String key) {
        return paymentScope(tenant).and(TRADE_PAYMENT_DO.PAYMENT_ID.eq(key));
    }
    private TradePaymentProjection read(TradePaymentDO row) {
        try {
            var value = codec.payment(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getPaymentId().equals(value.paymentId())
                    || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId())
                    || !row.getState().equals(value.state())
                    || !row.getBusinessCreatedAt().equals(value.initiatedAt())) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public ProjectionAdvance applyPaymentIfNewer(String tenant, TradePaymentProjection value, String factsHash) {
        writable();
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = payments.selectOneByQuery(paymentKey(tenant, value.paymentId()).forUpdate());
        if (row == null) {
            if (payments.insert(converter.toDO(ids.generate(), tenant, value, factsHash, payload)) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number()) return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        int changed = payments.updateByQuery(converter.toPatch(value, factsHash, payload), true,
                paymentKey(tenant, value.paymentId()).and(TRADE_PAYMENT_DO.SOURCE_VERSION.eq(row.getSourceVersion())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    private QueryWrapper refundScope(String tenant) {
        return QueryWrapper.create().where(TRADE_REFUND_DO.TENANT_ID.eq(tenant)).and(TRADE_REFUND_DO.DELETED.eq(0));
    }
    private QueryWrapper refundKey(String tenant, String key) {
        return refundScope(tenant).and(TRADE_REFUND_DO.REFUND_ID.eq(key));
    }
    private TradeRefundProjection read(TradeRefundDO row) {
        try {
            var value = codec.refund(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getRefundId().equals(value.refundId())
                    || !row.getRefundRequestId().equals(value.refundRequestId())
                    || !row.getPaymentId().equals(value.paymentId())
                    || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId())
                    || !row.getState().equals(value.state())
                    || !row.getBusinessCreatedAt().equals(value.refundCreatedAt())) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public ProjectionAdvance applyRefundIfNewer(String tenant, TradeRefundProjection value, String factsHash) {
        writable();
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = refunds.selectOneByQuery(refundKey(tenant, value.refundId()).forUpdate());
        if (row == null) {
            if (refunds.insert(converter.toDO(ids.generate(), tenant, value, factsHash, payload)) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number()) return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        int changed = refunds.updateByQuery(converter.toPatch(value, factsHash, payload), true,
                refundKey(tenant, value.refundId()).and(TRADE_REFUND_DO.SOURCE_VERSION.eq(row.getSourceVersion())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    private QueryWrapper summaryScope(String tenant) {
        return QueryWrapper.create().where(TRADE_SUMMARY_DO.TENANT_ID.eq(tenant)).and(TRADE_SUMMARY_DO.DELETED.eq(0));
    }
    private QueryWrapper summaryKey(String tenant, String key) {
        return summaryScope(tenant).and(TRADE_SUMMARY_DO.ORDER_ID.eq(key));
    }
    private TradeRefundSummary read(TradeSummaryDO row) {
        try {
            var value = codec.summary(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId())) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public ProjectionAdvance applyRefundSummaryIfNewer(String tenant, TradeRefundSummary value, String factsHash) {
        writable();
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = summaries.selectOneByQuery(summaryKey(tenant, value.orderId()).forUpdate());
        if (row == null) {
            if (summaries.insert(converter.toDO(ids.generate(), tenant, value, factsHash, payload)) != 1)
                throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number()) return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        int changed = summaries.updateByQuery(converter.toPatch(value, factsHash, payload), true,
                summaryKey(tenant, value.orderId()).and(TRADE_SUMMARY_DO.SOURCE_VERSION.eq(row.getSourceVersion())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    @Override public Optional<TradeOrderProjection> findOrder(String tenant, String orderId) {
        return Optional.ofNullable(orders.selectOneByQuery(orderKey(tenant, orderId))).map(this::read);
    }
    @Override public Optional<TradeRefundSummary> findRefundSummary(String tenant, String orderId) {
        return Optional.ofNullable(summaries.selectOneByQuery(summaryKey(tenant, orderId))).map(this::read);
    }
    @Override public Optional<TradeOrderProjection> findOrder(String tenant, String payee, String orderId) {
        return Optional.ofNullable(orders.selectOneByQuery(orderKey(tenant, orderId).and(TRADE_ORDER_DO.PAYEE_MERCHANT_ID.eq(payee)))).map(this::read);
    }
    @Override public Optional<TradeRefundSummary> findRefundSummary(String tenant, String payee, String orderId) {
        return Optional.ofNullable(summaries.selectOneByQuery(summaryKey(tenant, orderId).and(TRADE_SUMMARY_DO.PAYEE_MERCHANT_ID.eq(payee)))).map(this::read);
    }
    @Override public Map<String, TradeRefundSummary> refundSummaries(String tenant, String payee, Set<String> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        if (orderIds.size() > 100) throw new CommonException(INVALID_ARGUMENT);
        var values = summaries.selectListByQuery(summaryScope(tenant).and(TRADE_SUMMARY_DO.PAYEE_MERCHANT_ID.eq(payee))
                .and(TRADE_SUMMARY_DO.ORDER_ID.in(orderIds)));
        var result = new HashMap<String, TradeRefundSummary>();
        for (var row : values) { var value = read(row); result.put(value.orderId(), value); }
        return Map.copyOf(result);
    }

    @Override public PageResult<TradeOrderProjection> orders(String tenant, TradePageFilter filter) {
        var query = orderScope(tenant).and(TRADE_ORDER_DO.PAYEE_MERCHANT_ID.eq(filter.payeeMerchantId()));
        if (filter.state() != null) query.and(TRADE_ORDER_DO.STATE.eq(filter.state()));
        if (filter.createdFrom() != null) query.and(TRADE_ORDER_DO.BUSINESS_CREATED_AT.ge(filter.createdFrom()));
        if (filter.createdTo() != null) query.and(TRADE_ORDER_DO.BUSINESS_CREATED_AT.lt(filter.createdTo()));
        if (filter.merchantOrderNo() != null) query.and(TRADE_ORDER_DO.MERCHANT_ORDER_NO.eq(filter.merchantOrderNo()));

        long total = orders.selectCountByQuery(query);
        query.orderBy(TRADE_ORDER_DO.BUSINESS_CREATED_AT.desc(), TRADE_ORDER_DO.ORDER_ID.desc())
                .limit(filter.size()).offset((long) (filter.page() - 1) * filter.size());
        return new PageResult<>(orders.selectListByQuery(query).stream().map(this::read).toList(), total, filter.page(), filter.size());
    }

    @Override public PageResult<TradePaymentProjection> payments(String tenant, TradePageFilter filter) {
        var query = paymentScope(tenant).and(TRADE_PAYMENT_DO.PAYEE_MERCHANT_ID.eq(filter.payeeMerchantId()));
        if (filter.state() != null) query.and(TRADE_PAYMENT_DO.STATE.eq(filter.state()));
        if (filter.createdFrom() != null) query.and(TRADE_PAYMENT_DO.BUSINESS_CREATED_AT.ge(filter.createdFrom()));
        if (filter.createdTo() != null) query.and(TRADE_PAYMENT_DO.BUSINESS_CREATED_AT.lt(filter.createdTo()));
        if (filter.orderId() != null) query.and(TRADE_PAYMENT_DO.ORDER_ID.eq(filter.orderId()));

        long total = payments.selectCountByQuery(query);
        query.orderBy(TRADE_PAYMENT_DO.BUSINESS_CREATED_AT.desc(), TRADE_PAYMENT_DO.PAYMENT_ID.desc())
                .limit(filter.size()).offset((long) (filter.page() - 1) * filter.size());
        return new PageResult<>(payments.selectListByQuery(query).stream().map(this::read).toList(), total, filter.page(), filter.size());
    }

    @Override public PageResult<TradeRefundProjection> refunds(String tenant, TradePageFilter filter) {
        var query = refundScope(tenant).and(TRADE_REFUND_DO.PAYEE_MERCHANT_ID.eq(filter.payeeMerchantId()));
        if (filter.state() != null) query.and(TRADE_REFUND_DO.STATE.eq(filter.state()));
        if (filter.createdFrom() != null) query.and(TRADE_REFUND_DO.BUSINESS_CREATED_AT.ge(filter.createdFrom()));
        if (filter.createdTo() != null) query.and(TRADE_REFUND_DO.BUSINESS_CREATED_AT.lt(filter.createdTo()));
        if (filter.orderId() != null) query.and(TRADE_REFUND_DO.ORDER_ID.eq(filter.orderId()));
        if (filter.refundRequestId() != null) query.and(TRADE_REFUND_DO.REFUND_REQUEST_ID.eq(filter.refundRequestId()));
        long total = refunds.selectCountByQuery(query);
        query.orderBy(TRADE_REFUND_DO.BUSINESS_CREATED_AT.desc(), TRADE_REFUND_DO.REFUND_ID.desc())
                .limit(filter.size()).offset((long) (filter.page() - 1) * filter.size());
        return new PageResult<>(refunds.selectListByQuery(query).stream().map(this::read).toList(), total, filter.page(), filter.size());
    }
    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new CommonException(INVALID_STATE);
    }
}
