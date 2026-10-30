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
package io.github.loadup.pay.limitcenter.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitBucketDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitOperationGateDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationDO;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitBucketMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitExecutionGrantMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitReservationItemMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitReservationMapper;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class MerchantLimitGatewayImpl extends ServiceImpl<LimitReservationMapper, LimitReservationDO>
        implements MerchantLimitGateway {
    private static final String BUCKET_COLUMNS = "id, created_at, updated_at, rule_scope_id, merchant_id, metric, "
            + "currency, window_type, window_start, window_end, used_value, held_value, row_version";
    private static final String BUCKET_KEY = "rule_scope_id = :scope AND merchant_id = :merchant "
            + "AND metric = :metric AND currency = :currency AND window_type = :window "
            + "AND window_start = :start AND window_end = :end AND deleted = FALSE";
    private static final Comparator<LimitBucketKey> BUCKET_ORDER = Comparator.comparing(LimitBucketKey::ruleScopeId)
            .thenComparing(LimitBucketKey::merchantId)
            .thenComparing(key -> key.metric().name())
            .thenComparing(LimitBucketKey::currency)
            .thenComparing(key -> key.window().name())
            .thenComparing(key -> key.bounds().start())
            .thenComparing(key -> key.bounds().end());
    private final LimitStorageConverter converter;
    private final NamedParameterJdbcTemplate jdbc;
    private final LimitReservationItemMapper reservationItems;
    private final LimitExecutionGrantMapper executionGrants;
    private final LimitBucketMapper buckets;
    private final IdGenerator ids;
    private final Clock clock;

    public MerchantLimitGatewayImpl(
            LimitStorageConverter converter,
            NamedParameterJdbcTemplate jdbc,
            LimitReservationMapper mapper,
            LimitReservationItemMapper reservationItems,
            LimitExecutionGrantMapper executionGrants,
            LimitBucketMapper buckets,
            IdGenerator ids,
            Clock clock) {
        this.converter = converter;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.reservationItems = reservationItems;
        this.executionGrants = executionGrants;
        this.buckets = buckets;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Optional<LimitOperationGate> lockGate(String tenant, String operation) {
        requireTransaction(tenant);
        var args = new MapSqlParameterSource("operation", operation);
        return jdbc.query("""
                SELECT id, created_at, updated_at, operation_id, payment_id, state,
                       payment_source_version, no_collection_evidence_id, row_version
                FROM limit_operation_gate WHERE operation_id = :operation
                    AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(LimitOperationGateDO.class)).stream().findFirst().map(converter::toGate);
    }

    @Override
    public LimitOperationGate lockOrCreateGate(String tenant, String operation, String payment) {
        requireTransaction(tenant);
        var existing = lockGate(tenant, operation);
        if (existing.isPresent()) {
            return existing.get();
        }
        // Do not endlessly retry a permanent collision with a payment bound to a different operation.
        var args = new MapSqlParameterSource("payment", payment);
        if (!jdbc.query("SELECT id FROM limit_operation_gate WHERE payment_id = :payment AND deleted = FALSE",
                args, (rs, index) -> rs.getString(1)).isEmpty()) {
            // Another transaction may have created this same operation since the first lookup.
            return lockGate(tenant, operation).orElseThrow(() -> new CommonException(IDEMPOTENCY_CONFLICT));
        }
        var gate = LimitOperationGate.init(operation, payment);
        var row = converter.toGateDO(tenant, gate, 0);
        var now = LocalDateTime.now(clock);
        row.setId(ids.generate());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            jdbc.update("""
                    INSERT INTO limit_operation_gate (id, created_at, updated_at, deleted, operation_id,
                        payment_id, state, payment_source_version, no_collection_evidence_id, row_version)
                    VALUES (:id, :createdAt, :updatedAt, FALSE, :operationId, :paymentId, :state,
                        :paymentSourceVersion, :noCollectionEvidenceId, :rowVersion)
                    ON CONFLICT (operation_id) DO NOTHING
                    """, new BeanPropertySqlParameterSource(row));
        } catch (DuplicateKeyException collision) {
            for (Throwable cause = collision; cause != null; cause = cause.getCause()) {
                if (cause instanceof PSQLException sql && "23505".equals(sql.getSQLState())
                        && sql.getServerErrorMessage() != null
                        && "uk_limit_gate_payment".equals(sql.getServerErrorMessage().getConstraint())) {
                    throw new LimitWriteRaceException("Payment gate was concurrently created", collision);
                }
            }
            throw collision;
        }
        return lockGate(tenant, operation).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
    }

    @Override
    public Optional<StoredLimitReservation> findByOperation(String tenant, String operation) {
        requireTenant(tenant);
        try {
            return Optional.ofNullable(getOne(QueryWrapper.create().eq("operation_id", operation)))
                    .map(converter::toStored);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private MapSqlParameterSource bucketKey(LimitBucketKey key) {
        return new MapSqlParameterSource().addValue("scope", key.ruleScopeId())
                .addValue("merchant", key.merchantId()).addValue("metric", key.metric().name())
                .addValue("currency", key.currency()).addValue("window", key.window().name())
                .addValue("start", key.bounds().start()).addValue("end", key.bounds().end());
    }

    private Optional<LimitBucketDO> bucket(LimitBucketKey key, boolean lock) {
        return jdbc.query("SELECT " + BUCKET_COLUMNS + " FROM limit_bucket WHERE " + BUCKET_KEY
                        + (lock ? " FOR UPDATE" : ""), bucketKey(key),
                BeanPropertyRowMapper.newInstance(LimitBucketDO.class)).stream().findFirst();
    }

    @Override
    public LimitBucketSet lockBuckets(String tenant, List<LimitBucketKey> keys) {
        return lockBuckets(tenant, keys, true);
    }

    @Override
    public LimitBucketSet lockExistingBuckets(String tenant, List<LimitBucketKey> keys) {
        return lockBuckets(tenant, keys, false);
    }

    private LimitBucketSet lockBuckets(String tenant, List<LimitBucketKey> keys, boolean createMissing) {
        requireTransaction(tenant);
        var locked = new LinkedHashMap<LimitBucketKey, LimitBucket>();
        for (var key : keys.stream().sorted(BUCKET_ORDER).toList()) {
            if (locked.containsKey(key)) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var row = bucket(key, true).orElse(null);
            if (row == null) {
                if (!createMissing) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var initial = new LimitBucket(key, 0, 0, 0);
                var created = converter.toBucketDO(tenant, initial);
                var now = LocalDateTime.now(clock);
                created.setId(ids.generate());
                created.setCreatedAt(now);
                created.setUpdatedAt(now);
                jdbc.update("""
                        INSERT INTO limit_bucket (id, created_at, updated_at, deleted, rule_scope_id,
                            merchant_id, metric, currency, window_type, window_start, window_end, used_value, held_value, row_version)
                        VALUES (:id, :createdAt, :updatedAt, FALSE, :ruleScopeId, :merchantId, :metric,
                            :currency, :windowType, :windowStart, :windowEnd, :usedValue, :heldValue, :rowVersion)
                        ON CONFLICT (rule_scope_id, merchant_id, metric, currency, window_type, window_start, window_end)
                        DO NOTHING
                        """, new BeanPropertySqlParameterSource(created));
                row = bucket(key, true).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            }
            locked.put(key, converter.toBucket(row));
        }
        return new LimitBucketSet(locked);
    }

    @Override
    public void saveGate(String tenant, LimitOperationGate gate) {
        requireTransaction(tenant);
        gate.requireOpen();
        var args = new MapSqlParameterSource("operation", gate.operationId())
                .addValue("payment", gate.paymentId()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE limit_operation_gate SET state = 'OPEN', row_version = 1, updated_at = :updated
                WHERE operation_id = :operation AND payment_id = :payment
                    AND state = 'INIT' AND row_version = 0 AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void insertReservation(
            String tenant,
            StoredLimitReservation stored,
            List<LimitReservationItem> details,
            LimitBucketSet before,
            LimitBucketSet after) {
        requireTransaction(tenant);
        if (!before.buckets().keySet().equals(after.buckets().keySet())
                || details.size() != before.buckets().size()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        updateBuckets(tenant, before, after);
        var reservation = converter.toReservationDO(tenant, stored);
        var now = LocalDateTime.now(clock);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        int inserted = jdbc.update("""
                INSERT INTO limit_reservation (id, created_at, updated_at, deleted, operation_id, payment_id,
                    order_id, payee_merchant_id, amount_minor, currency, policy_id, policy_version, policy_hash,
                    request_digest, caller_service_id, initiated_at, state, reserve_expires_at, active_attempt_id,
                    execution_epoch, payment_source_version, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :operationId, :paymentId, :orderId,
                    :payeeMerchantId, :amountMinor, :currency, :policyId, :policyVersion, :policyHash, :requestDigest,
                    :callerServiceId, :initiatedAt, :state, :reserveExpiresAt, :activeAttemptId, :executionEpoch,
                    :paymentSourceVersion, :rowVersion)
                """, new BeanPropertySqlParameterSource(reservation));
        if (inserted != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        var seen = new HashSet<LimitBucketKey>();
        for (var item : details) {
            if (!item.reservationId().equals(stored.reservation().id())
                    || !seen.add(item.bucket())
                    || !before.buckets().containsKey(item.bucket())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var row = bucket(item.bucket(), false).orElse(null);
            if (row == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var detail = converter.toItemDO(tenant, item, row.getId());
            detail.setCreatedAt(now);
            detail.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO limit_reservation_item (id, created_at, updated_at, deleted, reservation_id,
                        bucket_id, reserved_value, consumed_value, restored_value)
                    VALUES (:id, :createdAt, :updatedAt, FALSE, :reservationId, :bucketId,
                        :reservedValue, :consumedValue, :restoredValue)
                    """, new BeanPropertySqlParameterSource(detail));
            if (count != 1) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
        }
    }

    @Override
    public void updateBuckets(String tenant, LimitBucketSet before, LimitBucketSet after) {
        requireTransaction(tenant);
        if (!before.buckets().keySet().equals(after.buckets().keySet())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        for (var key : before.buckets().keySet().stream().sorted(BUCKET_ORDER).toList()) {
            if (before.buckets().get(key).equals(after.buckets().get(key))) {
                continue;
            }
            var next = after.buckets().get(key);
            var args = bucketKey(key).addValue("used", next.used()).addValue("held", next.held())
                    .addValue("version", next.version()).addValue("expected", before.buckets().get(key).version())
                    .addValue("updated", LocalDateTime.now(clock));
            int changed = jdbc.update("UPDATE limit_bucket SET used_value = :used, held_value = :held, "
                    + "row_version = :version, updated_at = :updated WHERE " + BUCKET_KEY + " AND row_version = :expected", args);
            if (changed != 1) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
        }
    }

    @Override
    public void updateReservation(String tenant, StoredLimitReservation stored, long expectedVersion) {
        requireTransaction(tenant);
        var value = stored.reservation();
        var args = new MapSqlParameterSource("id", value.id())
                .addValue("operation", value.operationId()).addValue("payment", value.paymentId())
                .addValue("state", value.state().name()).addValue("attempt", value.activeAttemptId())
                .addValue("epoch", value.executionEpoch()).addValue("source", value.paymentSourceVersion())
                .addValue("version", value.version()).addValue("expected", expectedVersion)
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE limit_reservation SET state = :state, active_attempt_id = :attempt, execution_epoch = :epoch,
                    payment_source_version = :source, row_version = :version, updated_at = :updated
                WHERE id = :id AND operation_id = :operation AND payment_id = :payment
                    AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public List<LimitReservationItem> loadItems(String tenant, String reservationId) {
        requireTenant(tenant);
        // Item mutation shares the operation gate. These reads never acquire bucket locks out of order.
        return reservationItems
                .selectListByQuery(QueryWrapper.create().eq("reservation_id", reservationId))
                .stream()
                .map(row -> {
                    var bucket = Optional.ofNullable(buckets.selectOneById(row.getBucketId()))
                            .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                    return converter.toItem(row, converter.toKey(bucket));
                })
                .toList();
    }

    @Override
    public Optional<LimitExecutionGrant> findExecutionGrant(String tenant, String operation, long epoch) {
        requireTenant(tenant);
        try {
            var query = QueryWrapper.create()
                    .eq("operation_id", operation)
                    .eq("execution_epoch", epoch);
            return Optional.ofNullable(executionGrants.selectOneByQuery(query))
                    .map(converter::toExecutionGrant);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertExecutionGrant(String tenant, LimitExecutionGrant grant) {
        requireTransaction(tenant);
        var row = converter.toExecutionGrantDO(tenant, grant);
        var now = LocalDateTime.now(clock);
        row.setId(ids.generate());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int inserted = jdbc.update("""
                INSERT INTO limit_execution_grant (id, created_at, updated_at, deleted, operation_id,
                    payment_id, attempt_id, execution_epoch, request_key, permit_key_id, permit_hash)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :operationId, :paymentId, :attemptId,
                    :executionEpoch, :requestKey, :permitKeyId, :permitHash)
                """, new BeanPropertySqlParameterSource(row));
        if (inserted != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void requireTenant(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private void requireTransaction(String tenant) {
        requireTenant(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
