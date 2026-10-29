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
package io.github.loadup.pay.acquire.infrastructure.repository;

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationFilter;
import io.github.loadup.pay.acquire.infrastructure.converter.MerchantNotificationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.MerchantNotificationDeliveryDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class MerchantNotificationGatewayImpl implements MerchantNotificationGateway {
    private static final String COLUMNS = "id, tenant_id, created_at, updated_at, event_id, order_id, payee_merchant_id, "
            + "event_type, aggregate_id, source_version, body, body_digest, target_ref, signing_identity_ref, timeout_millis, "
            + "retry_initial_seconds, retry_multiplier, retry_maximum_seconds, retry_max_attempts, retry_window_seconds, "
            + "occurred_at, expires_at, next_attempt_at, attempt_count, state, route_fingerprint, acknowledged_at, row_version";
    private final Clock clock;
    private final NamedParameterJdbcTemplate jdbc;
    private final MerchantNotificationStorageConverter converter;

    public MerchantNotificationGatewayImpl(MerchantNotificationStorageConverter converter, NamedParameterJdbcTemplate jdbc,
                                           @Qualifier("acquireClock") Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public Optional<MerchantNotificationDelivery> find(String tenant, String deliveryId) {
        tenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", deliveryId);
        var row = read("id = :id", args, false);
        return row == null ? Optional.empty() : Optional.of(decode(row));
    }

    @Override
    public PageResult<MerchantNotificationDelivery> page(String tenant, MerchantNotificationFilter filter) {
        tenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("payee", filter.payeeMerchantId())
                .addValue("limit", filter.size()).addValue("offset", (long) (filter.page() - 1) * filter.size());
        var where = new StringBuilder("tenant_id = :tenant AND deleted = FALSE AND payee_merchant_id = :payee");
        if (filter.orderId() != null) {
            where.append(" AND order_id = :orderId");
            args.addValue("orderId", filter.orderId());
        }
        if (filter.eventId() != null) {
            where.append(" AND event_id = :eventId");
            args.addValue("eventId", filter.eventId());
        }
        if (filter.state() != null) {
            where.append(" AND state = :state");
            args.addValue("state", filter.state().name());
        }
        if (filter.occurredFrom() != null) {
            where.append(" AND occurred_at >= :fromTime");
            args.addValue("fromTime", filter.occurredFrom());
        }
        if (filter.occurredTo() != null) {
            where.append(" AND occurred_at < :toTime");
            args.addValue("toTime", filter.occurredTo());
        }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM merchant_notification_delivery WHERE " + where, args, Long.class);
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM merchant_notification_delivery WHERE " + where
                + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset", args, BeanPropertyRowMapper.newInstance(MerchantNotificationDeliveryDO.class));
        return PageResult.of(rows.stream().map(this::decode).toList(), total, filter.page(), filter.size());
    }

    @Override
    public Optional<MerchantNotificationDelivery> findEvent(String tenant, String eventId, String targetRef) {
        tenant(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("event", eventId).addValue("target", targetRef);
        var row = read("event_id = :event AND target_ref = :target", args, false);
        return row == null ? Optional.empty() : Optional.of(decode(row));
    }

    @Override
    public Optional<MerchantNotificationDelivery> lock(String tenant, String deliveryId) {
        writable(tenant);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", deliveryId);
        var row = read("id = :id", args, true);
        return row == null ? Optional.empty() : Optional.of(decode(row));
    }

    @Override
    public void insert(String tenant, MerchantNotificationDelivery delivery) {
        writable(tenant);
        if (delivery.rowVersion() != 0) throw new CommonException(SNAPSHOT_INVALID);
        var row = converter.toDO(tenant, delivery);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO merchant_notification_delivery (id, tenant_id, created_at, updated_at, deleted, event_id, order_id,
                    payee_merchant_id, event_type, aggregate_id, source_version, body, body_digest, target_ref, signing_identity_ref,
                    timeout_millis, retry_initial_seconds, retry_multiplier, retry_maximum_seconds, retry_max_attempts,
                    retry_window_seconds, occurred_at, expires_at, next_attempt_at, attempt_count, state, route_fingerprint, acknowledged_at, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :eventId, :orderId, :payeeMerchantId, :eventType, :aggregateId,
                    :sourceVersion, :body, :bodyDigest, :targetRef, :signingIdentityRef, :timeoutMillis, :retryInitialSeconds,
                    :retryMultiplier, :retryMaximumSeconds, :retryMaxAttempts, :retryWindowSeconds, :occurredAt, :expiresAt,
                    :nextAttemptAt, :attemptCount, :state, :routeFingerprint, :acknowledgedAt, :rowVersion)
                ON CONFLICT (tenant_id, event_id, target_ref) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new AcquireWriteConflictException("Notification event target already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, MerchantNotificationDelivery before, MerchantNotificationDelivery after) {
        writable(tenant);
        if (!sameIdentity(before, after) || after.rowVersion() != increment(before.rowVersion())
                || before.routeFingerprint() != null && !before.routeFingerprint().equals(after.routeFingerprint()))
            throw new CommonException(SNAPSHOT_INVALID);
        var patch = converter.toPatch(after);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", before.id()).addValue("expected", before.rowVersion())
                .addValue("next", patch.getNextAttemptAt()).addValue("attempts", patch.getAttemptCount()).addValue("state", patch.getState())
                .addValue("fingerprint", patch.getRouteFingerprint()).addValue("acknowledged", patch.getAcknowledgedAt())
                .addValue("version", patch.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE merchant_notification_delivery SET next_attempt_at = :next, attempt_count = :attempts, state = :state,
                    route_fingerprint = COALESCE(:fingerprint, route_fingerprint), acknowledged_at = COALESCE(:acknowledged, acknowledged_at),
                    row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private MerchantNotificationDeliveryDO read(String predicate, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("SELECT " + COLUMNS + " FROM merchant_notification_delivery WHERE tenant_id = :tenant AND deleted = FALSE AND "
                        + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(MerchantNotificationDeliveryDO.class))
                .stream().findFirst().orElse(null);
    }

    private MerchantNotificationDelivery decode(io.github.loadup.pay.acquire.infrastructure.dataobject.MerchantNotificationDeliveryDO row) {
        try {
            var value = converter.toDomain(row);
            if (row.getId() == null || row.getTenantId() == null || row.getRowVersion() == null
                    || row.getRowVersion() < 0 || !row.getId().equals(value.id())
                    || row.getRowVersion() != value.rowVersion() || !row.getTargetRef().equals(value.policy().targetRef()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private boolean sameIdentity(MerchantNotificationDelivery before, MerchantNotificationDelivery after) {
        return before != null && after != null && before.id().equals(after.id())
                && before.eventId().equals(after.eventId()) && before.orderId().equals(after.orderId())
                && before.payeeMerchantId().equals(after.payeeMerchantId()) && before.eventType().equals(after.eventType())
                && before.aggregateId().equals(after.aggregateId()) && before.sourceVersion() == after.sourceVersion()
                && before.body().equals(after.body()) && before.bodyDigest().equals(after.bodyDigest())
                && before.policy().equals(after.policy()) && before.occurredAt().equals(after.occurredAt())
                && before.expiresAt().equals(after.expiresAt()) && before.rowVersion() >= 0;
    }

    private long increment(long version) {
        try {
            return Math.incrementExact(version);
        } catch (ArithmeticException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void tenant(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void writable(String tenant) {
        tenant(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly() || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
