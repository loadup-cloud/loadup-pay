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
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.domain.gateway.MerchantLimitGateway;
import io.github.loadup.pay.limitcenter.domain.model.GateState;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitExecutionGrant;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitBucketDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitExecutionGrantDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitOperationGateDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitReservationItemDO;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitBucketDOMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitExecutionGrantDOMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitOperationGateDOMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitReservationDOMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitReservationItemDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class MerchantLimitGatewayImpl implements MerchantLimitGateway {
    private static final Comparator<LimitBucketKey> BUCKET_ORDER = Comparator.comparing(LimitBucketKey::ruleScopeId)
            .thenComparing(LimitBucketKey::merchantId)
            .thenComparing(key -> key.metric().name())
            .thenComparing(LimitBucketKey::currency)
            .thenComparing(key -> key.window().name())
            .thenComparing(key -> key.bounds().start())
            .thenComparing(key -> key.bounds().end());
    private final LimitOperationGateDOMapper gates;
    private final LimitBucketDOMapper buckets;
    private final LimitReservationDOMapper reservations;
    private final LimitReservationItemDOMapper items;
    private final LimitStorageConverter converter;
    private final LimitExecutionGrantDOMapper grants;

    public MerchantLimitGatewayImpl(
            LimitOperationGateDOMapper gates,
            LimitBucketDOMapper buckets,
            LimitReservationDOMapper reservations,
            LimitReservationItemDOMapper items,
            LimitStorageConverter converter,
            LimitExecutionGrantDOMapper grants) {
        this.gates = gates;
        this.buckets = buckets;
        this.reservations = reservations;
        this.items = items;
        this.converter = converter;
        this.grants = grants;
    }

    private QueryWrapper gateQuery(String tenant, String operation) {
        return QueryWrapper.create()
                .where(LimitOperationGateDO::getTenantId)
                .eq(tenant)
                .and(LimitOperationGateDO::getDeleted)
                .eq(0)
                .and(LimitOperationGateDO::getOperationId)
                .eq(operation);
    }

    @Override
    public Optional<LimitOperationGate> lockGate(String tenant, String operation) {
        return Optional.ofNullable(
                        gates.selectOneByQuery(gateQuery(tenant, operation).forUpdate()))
                .map(converter::toGate);
    }

    @Override
    public LimitOperationGate lockOrCreateGate(String tenant, String operation, String payment) {
        var row = gates.selectOneByQuery(gateQuery(tenant, operation).forUpdate());
        if (row != null) return converter.toGate(row);
        // Do not endlessly retry a permanent collision with a payment bound to a different operation.
        var paymentRow = gates.selectOneByQuery(QueryWrapper.create()
                .where(LimitOperationGateDO::getTenantId)
                .eq(tenant)
                .and(LimitOperationGateDO::getDeleted)
                .eq(0)
                .and(LimitOperationGateDO::getPaymentId)
                .eq(payment));
        if (paymentRow != null) throw new CommonException(IDEMPOTENCY_CONFLICT);
        var gate = LimitOperationGate.init(operation, payment);
        gates.insert(converter.toGateDO(tenant, gate, 0));
        return gate;
    }

    @Override
    public Optional<StoredLimitReservation> findByOperation(String tenant, String operation) {
        var row = reservations.selectOneByQuery(QueryWrapper.create()
                .where(LimitReservationDO::getTenantId)
                .eq(tenant)
                .and(LimitReservationDO::getDeleted)
                .eq(0)
                .and(LimitReservationDO::getOperationId)
                .eq(operation));
        try {
            return Optional.ofNullable(row).map(converter::toStored);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private QueryWrapper bucketQuery(String tenant, LimitBucketKey key) {
        return QueryWrapper.create()
                .where(LimitBucketDO::getTenantId)
                .eq(tenant)
                .and(LimitBucketDO::getDeleted)
                .eq(0)
                .and(LimitBucketDO::getRuleScopeId)
                .eq(key.ruleScopeId())
                .and(LimitBucketDO::getMerchantId)
                .eq(key.merchantId())
                .and(LimitBucketDO::getMetric)
                .eq(key.metric().name())
                .and(LimitBucketDO::getCurrency)
                .eq(key.currency())
                .and(LimitBucketDO::getWindowType)
                .eq(key.window().name())
                .and(LimitBucketDO::getWindowStart)
                .eq(key.bounds().start())
                .and(LimitBucketDO::getWindowEnd)
                .eq(key.bounds().end());
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
        var locked = new LinkedHashMap<LimitBucketKey, LimitBucket>();
        for (var key : keys.stream().sorted(BUCKET_ORDER).toList()) {
            if (locked.containsKey(key)) throw new CommonException(SNAPSHOT_INVALID);
            var row = buckets.selectOneByQuery(bucketQuery(tenant, key).forUpdate());
            if (row == null) {
                if (!createMissing) throw new CommonException(SNAPSHOT_INVALID);
                var initial = new LimitBucket(key, 0, 0, 0);
                buckets.insert(converter.toBucketDO(tenant, initial));
                locked.put(key, initial);
            } else locked.put(key, converter.toBucket(row));
        }
        return new LimitBucketSet(locked);
    }

    @Override
    public void saveGate(String tenant, LimitOperationGate gate) {
        gate.requireOpen();
        int changed = gates.updateByQuery(
                converter.toGateDO(tenant, gate, 1),
                true,
                gateQuery(tenant, gate.operationId())
                        .and(LimitOperationGateDO::getPaymentId)
                        .eq(gate.paymentId())
                        .and(LimitOperationGateDO::getState)
                        .eq(GateState.INIT.name())
                        .and(LimitOperationGateDO::getRowVersion)
                        .eq(0));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insertReservation(
            String tenant,
            StoredLimitReservation stored,
            List<LimitReservationItem> details,
            LimitBucketSet before,
            LimitBucketSet after) {
        if (!before.buckets().keySet().equals(after.buckets().keySet())
                || details.size() != before.buckets().size()) throw new CommonException(SNAPSHOT_INVALID);
        updateBuckets(tenant, before, after);
        reservations.insert(converter.toReservationDO(tenant, stored));
        var seen = new HashSet<LimitBucketKey>();
        for (var item : details) {
            if (!item.reservationId().equals(stored.reservation().id())
                    || !seen.add(item.bucket())
                    || !before.buckets().containsKey(item.bucket())) throw new CommonException(SNAPSHOT_INVALID);
            var row = buckets.selectOneByQuery(bucketQuery(tenant, item.bucket()));
            if (row == null) throw new CommonException(SNAPSHOT_INVALID);
            items.insert(converter.toItemDO(tenant, item, row.getId()));
        }
    }

    @Override
    public void updateBuckets(String tenant, LimitBucketSet before, LimitBucketSet after) {
        if (!before.buckets().keySet().equals(after.buckets().keySet())) throw new CommonException(SNAPSHOT_INVALID);
        for (var key : before.buckets().keySet().stream().sorted(BUCKET_ORDER).toList()) {
            int changed = buckets.updateByQuery(
                    converter.toBucketDO(tenant, after.buckets().get(key)),
                    true,
                    bucketQuery(tenant, key)
                            .and(LimitBucketDO::getRowVersion)
                            .eq(before.buckets().get(key).version()));
            if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void updateReservation(String tenant, StoredLimitReservation stored, long expectedVersion) {
        var value = stored.reservation();
        int changed = reservations.updateByQuery(
                converter.toReservationDO(tenant, stored),
                true,
                QueryWrapper.create()
                        .where(LimitReservationDO::getTenantId)
                        .eq(tenant)
                        .and(LimitReservationDO::getDeleted)
                        .eq(0)
                        .and(LimitReservationDO::getId)
                        .eq(value.id())
                        .and(LimitReservationDO::getOperationId)
                        .eq(value.operationId())
                        .and(LimitReservationDO::getPaymentId)
                        .eq(value.paymentId())
                        .and(LimitReservationDO::getRowVersion)
                        .eq(expectedVersion));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public List<LimitReservationItem> loadItems(String tenant, String reservationId) {
        // Item mutation shares the operation gate. These reads never acquire bucket locks out of order.
        var rows = items.selectListByQuery(QueryWrapper.create()
                .where(LimitReservationItemDO::getTenantId)
                .eq(tenant)
                .and(LimitReservationItemDO::getDeleted)
                .eq(0)
                .and(LimitReservationItemDO::getReservationId)
                .eq(reservationId));
        return rows.stream()
                .map(row -> {
                    var bucket = buckets.selectOneByQuery(QueryWrapper.create()
                            .where(LimitBucketDO::getTenantId)
                            .eq(tenant)
                            .and(LimitBucketDO::getDeleted)
                            .eq(0)
                            .and(LimitBucketDO::getId)
                            .eq(row.getBucketId()));
                    if (bucket == null) throw new CommonException(SNAPSHOT_INVALID);
                    return converter.toItem(row, converter.toKey(bucket));
                })
                .toList();
    }

    @Override
    public Optional<LimitExecutionGrant> findExecutionGrant(String tenant, String operation, long epoch) {
        var row = grants.selectOneByQuery(QueryWrapper.create()
                .where(LimitExecutionGrantDO::getTenantId)
                .eq(tenant)
                .and(LimitExecutionGrantDO::getDeleted)
                .eq(0)
                .and(LimitExecutionGrantDO::getOperationId)
                .eq(operation)
                .and(LimitExecutionGrantDO::getExecutionEpoch)
                .eq(epoch));
        try {
            return Optional.ofNullable(row).map(converter::toExecutionGrant);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertExecutionGrant(String tenant, LimitExecutionGrant grant) {
        grants.insert(converter.toExecutionGrantDO(tenant, grant));
    }
}
