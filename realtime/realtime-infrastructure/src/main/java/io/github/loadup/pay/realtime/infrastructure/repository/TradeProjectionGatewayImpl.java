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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.realtime.app.port.TradeProjectionCodec;
import io.github.loadup.pay.realtime.domain.gateway.ProjectionWriteRaceException;
import io.github.loadup.pay.realtime.domain.gateway.TradeProjectionGateway;
import io.github.loadup.pay.realtime.domain.gateway.TradeQueryGateway;
import io.github.loadup.pay.realtime.domain.model.ProjectionAdvance;
import io.github.loadup.pay.realtime.domain.model.ProjectionFacts;
import io.github.loadup.pay.realtime.domain.model.TradeOrderProjection;
import io.github.loadup.pay.realtime.domain.model.TradePageFilter;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;
import io.github.loadup.pay.realtime.infrastructure.converter.TradeStorageConverter;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeOrderDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradePaymentDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeRefundDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeSummaryDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.QUERY_FORBIDDEN;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SOURCE_CONFLICT;

@Repository
public class TradeProjectionGatewayImpl implements TradeProjectionGateway, TradeQueryGateway {
    private static final String ORDER_COLUMNS = "id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, "
            + "merchant_order_no, state, business_created_at, source_version, content_hash, payload, snapshot_at";
    private static final String PAYMENT_COLUMNS = "id, tenant_id, created_at, updated_at, payment_id, order_id, payee_merchant_id, "
            + "state, business_created_at, source_version, content_hash, payload, snapshot_at";
    private static final String REFUND_COLUMNS = "id, tenant_id, created_at, updated_at, refund_id, refund_request_id, payment_id, "
            + "order_id, payee_merchant_id, state, business_created_at, source_version, content_hash, payload, snapshot_at";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final TradeStorageConverter converter;
    private final TradeProjectionCodec codec;
    private final IdGenerator ids;

    public TradeProjectionGatewayImpl(TradeStorageConverter converter, TradeProjectionCodec codec, IdGenerator ids,
                                      NamedParameterJdbcTemplate jdbc, @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.codec = codec;
        this.ids = ids;
    }

    private Optional<TradeOrderDO> order(String tenant, String orderId, String payee, boolean lock) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("orderId", orderId).addValue("payee", payee);
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM trade_order_projection WHERE tenant_id = :tenant "
                        + "AND order_id = :orderId AND deleted = FALSE" + (payee == null ? "" : " AND payee_merchant_id = :payee")
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(TradeOrderDO.class))
                .stream().findFirst();
    }

    private TradeOrderProjection read(TradeOrderDO row) {
        try {
            var value = codec.order(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId())
                    || !row.getMerchantOrderNo().equals(value.merchantOrderNo())
                    || !row.getState().equals(value.state())
                    || !row.getBusinessCreatedAt().equals(value.orderCreatedAt()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public ProjectionAdvance applyOrderIfNewer(String tenant, TradeOrderProjection value, String factsHash) {
        writableJdbc(tenant);
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = order(tenant, value.orderId(), null, true).orElse(null);
        if (row == null) {
            var created = converter.toDO(ids.generate(), tenant, value, factsHash, payload);
            var now = LocalDateTime.now(clock);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO trade_order_projection (id, tenant_id, created_at, updated_at, deleted, order_id,
                        payee_merchant_id, merchant_order_no, state, business_created_at, source_version,
                        content_hash, payload, snapshot_at)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId,
                        :merchantOrderNo, :state, :businessCreatedAt, :sourceVersion, :contentHash, :payload, :snapshotAt)
                    ON CONFLICT (tenant_id, order_id) DO NOTHING
                    """, new BeanPropertySqlParameterSource(created));
            if (count == 0) throw new ProjectionWriteRaceException("Order projection business key already exists");
            if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number())
            return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        var args = new MapSqlParameterSource("tenant", tenant).addValue("orderId", value.orderId())
                .addValue("version", value.source().number()).addValue("expected", row.getSourceVersion())
                .addValue("state", value.state()).addValue("hash", factsHash).addValue("payload", payload)
                .addValue("snapshot", value.snapshotAt()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE trade_order_projection SET state = :state, source_version = :version, content_hash = :hash,
                    payload = :payload, snapshot_at = :snapshot, updated_at = :updated
                WHERE tenant_id = :tenant AND order_id = :orderId AND source_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    private Optional<TradePaymentDO> payment(String tenant, String paymentId, boolean lock) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("paymentId", paymentId);
        return jdbc.query("SELECT " + PAYMENT_COLUMNS + " FROM trade_payment_projection WHERE tenant_id = :tenant "
                        + "AND payment_id = :paymentId AND deleted = FALSE" + (lock ? " FOR UPDATE" : ""), args,
                BeanPropertyRowMapper.newInstance(TradePaymentDO.class)).stream().findFirst();
    }

    private TradePaymentProjection read(TradePaymentDO row) {
        try {
            var value = codec.payment(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getPaymentId().equals(value.paymentId())
                    || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId())
                    || !row.getState().equals(value.state())
                    || !row.getBusinessCreatedAt().equals(value.initiatedAt()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public ProjectionAdvance applyPaymentIfNewer(String tenant, TradePaymentProjection value, String factsHash) {
        writableJdbc(tenant);
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = payment(tenant, value.paymentId(), true).orElse(null);
        if (row == null) {
            var created = converter.toDO(ids.generate(), tenant, value, factsHash, payload);
            var now = LocalDateTime.now(clock);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO trade_payment_projection (id, tenant_id, created_at, updated_at, deleted, payment_id,
                        order_id, payee_merchant_id, state, business_created_at, source_version, content_hash, payload, snapshot_at)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :orderId, :payeeMerchantId,
                        :state, :businessCreatedAt, :sourceVersion, :contentHash, :payload, :snapshotAt)
                    ON CONFLICT (tenant_id, payment_id) DO NOTHING
                    """, new BeanPropertySqlParameterSource(created));
            if (count == 0) throw new ProjectionWriteRaceException("Payment projection business key already exists");
            if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number())
            return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        var args = new MapSqlParameterSource("tenant", tenant).addValue("paymentId", value.paymentId())
                .addValue("version", value.source().number()).addValue("expected", row.getSourceVersion())
                .addValue("state", value.state()).addValue("hash", factsHash).addValue("payload", payload)
                .addValue("snapshot", value.snapshotAt()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE trade_payment_projection SET state = :state, source_version = :version, content_hash = :hash,
                    payload = :payload, snapshot_at = :snapshot, updated_at = :updated
                WHERE tenant_id = :tenant AND payment_id = :paymentId AND source_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    private Optional<TradeRefundDO> refund(String tenant, String refundId, boolean lock) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("refundId", refundId);
        return jdbc.query("SELECT " + REFUND_COLUMNS + " FROM trade_refund_projection WHERE tenant_id = :tenant "
                        + "AND refund_id = :refundId AND deleted = FALSE" + (lock ? " FOR UPDATE" : ""), args,
                BeanPropertyRowMapper.newInstance(TradeRefundDO.class)).stream().findFirst();
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
                    || !row.getBusinessCreatedAt().equals(value.refundCreatedAt()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public ProjectionAdvance applyRefundIfNewer(String tenant, TradeRefundProjection value, String factsHash) {
        writableJdbc(tenant);
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = refund(tenant, value.refundId(), true).orElse(null);
        if (row == null) {
            var created = converter.toDO(ids.generate(), tenant, value, factsHash, payload);
            var now = LocalDateTime.now(clock);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO trade_refund_projection (id, tenant_id, created_at, updated_at, deleted, refund_id,
                        refund_request_id, payment_id, order_id, payee_merchant_id, state, business_created_at,
                        source_version, content_hash, payload, snapshot_at)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :refundId, :refundRequestId, :paymentId,
                        :orderId, :payeeMerchantId, :state, :businessCreatedAt, :sourceVersion, :contentHash, :payload, :snapshotAt)
                    ON CONFLICT (tenant_id, refund_id) DO NOTHING
                    """, new BeanPropertySqlParameterSource(created));
            if (count == 0) throw new ProjectionWriteRaceException("Refund projection business key already exists");
            if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number())
            return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        var args = new MapSqlParameterSource("tenant", tenant).addValue("refundId", value.refundId())
                .addValue("version", value.source().number()).addValue("expected", row.getSourceVersion())
                .addValue("state", value.state()).addValue("hash", factsHash).addValue("payload", payload)
                .addValue("snapshot", value.snapshotAt()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE trade_refund_projection SET state = :state, source_version = :version, content_hash = :hash,
                    payload = :payload, snapshot_at = :snapshot, updated_at = :updated
                WHERE tenant_id = :tenant AND refund_id = :refundId AND source_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    private Optional<TradeSummaryDO> summary(String tenant, String order, String payee, boolean lock) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("orderId", order).addValue("payee", payee);
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, source_version, "
                        + "content_hash, payload, snapshot_at FROM trade_refund_summary WHERE tenant_id = :tenant "
                        + "AND order_id = :orderId AND deleted = FALSE" + (payee == null ? "" : " AND payee_merchant_id = :payee")
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(TradeSummaryDO.class))
                .stream().findFirst();
    }

    private TradeRefundSummary read(TradeSummaryDO row) {
        try {
            var value = codec.summary(row.getPayload(), row.getContentHash(), row.getSnapshotAt());
            if (row.getSourceVersion() != value.source().number() || !row.getOrderId().equals(value.orderId())
                    || !row.getPayeeMerchantId().equals(value.payeeMerchantId()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public ProjectionAdvance applyRefundSummaryIfNewer(String tenant, TradeRefundSummary value, String factsHash) {
        writableJdbc(tenant);
        String payload = codec.encode(value);
        if (!codec.hash(payload).equals(factsHash)) throw new CommonException(SNAPSHOT_INVALID);
        var row = summary(tenant, value.orderId(), null, true).orElse(null);
        if (row == null) {
            var created = converter.toDO(ids.generate(), tenant, value, factsHash, payload);
            var now = LocalDateTime.now(clock);
            created.setCreatedAt(now);
            created.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO trade_refund_summary (id, tenant_id, created_at, updated_at, deleted, order_id,
                        payee_merchant_id, source_version, content_hash, payload, snapshot_at)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId,
                        :sourceVersion, :contentHash, :payload, :snapshotAt)
                    ON CONFLICT (tenant_id, order_id) DO NOTHING
                    """, new BeanPropertySqlParameterSource(created));
            if (count == 0) throw new ProjectionWriteRaceException("Refund summary business key already exists");
            if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
            return new ProjectionAdvance("APPLIED", value.source().number());
        }
        var previous = read(row);
        if (!ProjectionFacts.compatible(previous, value)) throw new CommonException(SOURCE_CONFLICT);
        if (row.getSourceVersion() > value.source().number())
            return new ProjectionAdvance("STALE", row.getSourceVersion());
        if (row.getSourceVersion() == value.source().number()) {
            if (!row.getContentHash().equals(factsHash)) throw new CommonException(SOURCE_CONFLICT);
            return new ProjectionAdvance("DUPLICATE", row.getSourceVersion());
        }
        var args = new MapSqlParameterSource("tenant", tenant).addValue("orderId", value.orderId())
                .addValue("version", value.source().number()).addValue("expected", row.getSourceVersion())
                .addValue("hash", factsHash).addValue("payload", payload).addValue("snapshot", value.snapshotAt())
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE trade_refund_summary SET source_version = :version, content_hash = :hash, payload = :payload,
                    snapshot_at = :snapshot, updated_at = :updated
                WHERE tenant_id = :tenant AND order_id = :orderId AND source_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        return new ProjectionAdvance("APPLIED", value.source().number());
    }

    @Override
    public Optional<TradeOrderProjection> findOrder(String tenant, String orderId) {
        return order(tenant, orderId, null, false).map(this::read);
    }

    @Override
    public Optional<TradeRefundSummary> findRefundSummary(String tenant, String orderId) {
        return summary(tenant, orderId, null, false).map(this::read);
    }

    @Override
    public Optional<TradeOrderProjection> findOrder(String tenant, String payee, String orderId) {
        return order(tenant, orderId, payee, false).map(this::read);
    }

    @Override
    public Optional<TradeRefundSummary> findRefundSummary(String tenant, String payee, String orderId) {
        return summary(tenant, orderId, payee, false).map(this::read);
    }

    @Override
    public Map<String, TradeRefundSummary> refundSummaries(String tenant, String payee, Set<String> orderIds) {
        requireTenant(tenant);
        if (orderIds.isEmpty()) return Map.of();
        if (orderIds.size() > 100) throw new CommonException(INVALID_ARGUMENT);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("payee", payee).addValue("orders", orderIds);
        var values = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, source_version,
                       content_hash, payload, snapshot_at FROM trade_refund_summary
                WHERE tenant_id = :tenant AND payee_merchant_id = :payee AND order_id IN (:orders) AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(TradeSummaryDO.class));
        var result = new HashMap<String, TradeRefundSummary>();
        for (var row : values) {
            var value = read(row);
            result.put(value.orderId(), value);
        }
        return Map.copyOf(result);
    }

    @Override
    public PageResult<TradeOrderProjection> orders(String tenant, TradePageFilter filter) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("payee", filter.payeeMerchantId())
                .addValue("size", filter.size()).addValue("offset", (long) (filter.page() - 1) * filter.size());
        var where = new StringBuilder(" WHERE tenant_id = :tenant AND payee_merchant_id = :payee AND deleted = FALSE");
        if (filter.state() != null) {
            where.append(" AND state = :state");
            args.addValue("state", filter.state());
        }
        if (filter.createdFrom() != null) {
            where.append(" AND business_created_at >= :fromTime");
            args.addValue("fromTime", filter.createdFrom());
        }
        if (filter.createdTo() != null) {
            where.append(" AND business_created_at < :toTime");
            args.addValue("toTime", filter.createdTo());
        }
        if (filter.merchantOrderNo() != null) {
            where.append(" AND merchant_order_no = :merchantOrder");
            args.addValue("merchantOrder", filter.merchantOrderNo());
        }
        String from = " FROM trade_order_projection" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, args, Long.class);
        var rows = jdbc.query("SELECT " + ORDER_COLUMNS + from
                        + " ORDER BY business_created_at DESC, order_id DESC LIMIT :size OFFSET :offset", args,
                BeanPropertyRowMapper.newInstance(TradeOrderDO.class));
        return new PageResult<>(rows.stream().map(this::read).toList(), total == null ? 0 : total, filter.page(), filter.size());
    }

    @Override
    public PageResult<TradePaymentProjection> payments(String tenant, TradePageFilter filter) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("payee", filter.payeeMerchantId())
                .addValue("size", filter.size()).addValue("offset", (long) (filter.page() - 1) * filter.size());
        var where = new StringBuilder(" WHERE tenant_id = :tenant AND payee_merchant_id = :payee AND deleted = FALSE");
        if (filter.state() != null) {
            where.append(" AND state = :state");
            args.addValue("state", filter.state());
        }
        if (filter.createdFrom() != null) {
            where.append(" AND business_created_at >= :fromTime");
            args.addValue("fromTime", filter.createdFrom());
        }
        if (filter.createdTo() != null) {
            where.append(" AND business_created_at < :toTime");
            args.addValue("toTime", filter.createdTo());
        }
        if (filter.orderId() != null) {
            where.append(" AND order_id = :orderId");
            args.addValue("orderId", filter.orderId());
        }
        String from = " FROM trade_payment_projection" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, args, Long.class);
        var rows = jdbc.query("SELECT " + PAYMENT_COLUMNS + from
                        + " ORDER BY business_created_at DESC, payment_id DESC LIMIT :size OFFSET :offset", args,
                BeanPropertyRowMapper.newInstance(TradePaymentDO.class));
        return new PageResult<>(rows.stream().map(this::read).toList(), total == null ? 0 : total, filter.page(), filter.size());
    }

    @Override
    public PageResult<TradeRefundProjection> refunds(String tenant, TradePageFilter filter) {
        requireTenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("payee", filter.payeeMerchantId())
                .addValue("size", filter.size()).addValue("offset", (long) (filter.page() - 1) * filter.size());
        var where = new StringBuilder(" WHERE tenant_id = :tenant AND payee_merchant_id = :payee AND deleted = FALSE");
        if (filter.state() != null) {
            where.append(" AND state = :state");
            args.addValue("state", filter.state());
        }
        if (filter.createdFrom() != null) {
            where.append(" AND business_created_at >= :fromTime");
            args.addValue("fromTime", filter.createdFrom());
        }
        if (filter.createdTo() != null) {
            where.append(" AND business_created_at < :toTime");
            args.addValue("toTime", filter.createdTo());
        }
        if (filter.orderId() != null) {
            where.append(" AND order_id = :orderId");
            args.addValue("orderId", filter.orderId());
        }
        if (filter.refundRequestId() != null) {
            where.append(" AND refund_request_id = :requestId");
            args.addValue("requestId", filter.refundRequestId());
        }
        String from = " FROM trade_refund_projection" + where;
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, args, Long.class);
        var rows = jdbc.query("SELECT " + REFUND_COLUMNS + from
                        + " ORDER BY business_created_at DESC, refund_id DESC LIMIT :size OFFSET :offset", args,
                BeanPropertyRowMapper.newInstance(TradeRefundDO.class));
        return new PageResult<>(rows.stream().map(this::read).toList(), total == null ? 0 : total, filter.page(), filter.size());
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }

    private void requireTenant(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(QUERY_FORBIDDEN);
    }

    private void writableJdbc(String tenant) {
        requireTenant(tenant);
        writable();
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
