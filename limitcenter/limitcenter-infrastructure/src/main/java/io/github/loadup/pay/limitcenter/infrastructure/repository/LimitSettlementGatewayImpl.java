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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitSettlementGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import io.github.loadup.pay.limitcenter.domain.model.GateState;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitSettlementReceipt;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitSettlementStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitSettlementReceiptDO;
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
import java.util.List;
import java.util.Optional;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class LimitSettlementGatewayImpl implements LimitSettlementGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final LimitSettlementStorageConverter converter;

    public LimitSettlementGatewayImpl(NamedParameterJdbcTemplate jdbc, LimitSettlementStorageConverter converter,
                                      @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<LimitSettlementReceipt> findReceipt(String tenant, String operation) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("operation", operation);
        var row = jdbc.query("SELECT id, tenant_id, created_at, updated_at, operation_id, payment_id, kind, "
                        + "business_event_id, request_digest, payment_version, evidence_id FROM limit_settlement_receipt "
                        + "WHERE tenant_id = :tenant AND operation_id = :operation AND deleted = FALSE", args,
                BeanPropertyRowMapper.newInstance(LimitSettlementReceiptDO.class)).stream().findFirst().orElse(null);
        if (row == null) return Optional.empty();
        try {
            return Optional.of(converter.toReceipt(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertReceipt(String tenant, LimitSettlementReceipt receipt) {
        transaction();
        scope(tenant);
        var row = converter.toDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO limit_settlement_receipt (id, tenant_id, created_at, updated_at, deleted, operation_id,
                    payment_id, kind, business_event_id, request_digest, payment_version, evidence_id)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :operationId, :paymentId, :kind,
                    :businessEventId, :requestDigest, :paymentVersion, :evidenceId)
                ON CONFLICT (tenant_id, operation_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new LimitWriteRaceException("Settlement receipt business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void fenceGate(String tenant, LimitOperationGate gate) {
        transaction();
        scope(tenant);
        if (gate.state() != GateState.FENCED) throw new CommonException(SNAPSHOT_INVALID);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("operation", gate.operationId());
        var row = jdbc.query("SELECT id, payment_id, row_version FROM limit_operation_gate "
                                + "WHERE tenant_id = :tenant AND operation_id = :operation AND deleted = FALSE", args,
                        (rs, index) -> new GateRow(rs.getString("id"), rs.getString("payment_id"), rs.getLong("row_version")))
                .stream().findFirst().orElse(null);
        if (row == null || !row.paymentId().equals(gate.paymentId()))
            throw new CommonException(SNAPSHOT_INVALID);
        args.addValue("id", row.id()).addValue("version", row.version())
                .addValue("next", Math.incrementExact(row.version())).addValue("source", gate.paymentSourceVersion())
                .addValue("evidence", gate.noCollectionEvidenceId()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE limit_operation_gate SET state = 'FENCED', payment_source_version = :source,
                    no_collection_evidence_id = :evidence, row_version = :next, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :version AND deleted = FALSE
                    AND state IN ('INIT', 'OPEN')
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateItems(String tenant, List<LimitReservationItem> before, List<LimitReservationItem> after) {
        transaction();
        scope(tenant);
        if (before.size() != after.size()) throw new CommonException(SNAPSHOT_INVALID);
        var changed = new HashMap<String, LimitReservationItem>();
        for (var value : after) if (changed.put(value.id(), value) != null) throw new CommonException(SNAPSHOT_INVALID);
        for (var previous : before) {
            var next = changed.get(previous.id());
            if (next == null || !previous.reservationId().equals(next.reservationId()) || !previous.bucket().equals(next.bucket())
                    || previous.reserved() != next.reserved()) throw new CommonException(SNAPSHOT_INVALID);
            if (previous.equals(next)) continue;
            var args = new MapSqlParameterSource("tenant", tenant).addValue("id", previous.id())
                    .addValue("reservation", previous.reservationId()).addValue("reserved", previous.reserved())
                    .addValue("consumed", previous.consumed()).addValue("restored", previous.restored())
                    .addValue("nextConsumed", next.consumed()).addValue("nextRestored", next.restored())
                    .addValue("updated", LocalDateTime.now(clock));
            int count = jdbc.update("""
                    UPDATE limit_reservation_item SET consumed_value = :nextConsumed, restored_value = :nextRestored,
                        updated_at = :updated WHERE tenant_id = :tenant AND id = :id AND reservation_id = :reservation
                        AND reserved_value = :reserved AND consumed_value = :consumed AND restored_value = :restored AND deleted = FALSE
                    """, args);
            if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private record GateRow(String id, String paymentId, long version) {
    }
}
