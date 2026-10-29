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
package io.github.loadup.pay.customer.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.gateway.MerchantAuthorizationGateway;
import io.github.loadup.pay.customer.domain.model.GrantState;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * All callers acquire the payee merchant row before modifying authorization records.
 */
@Repository
public class MerchantAuthorizationGatewayImpl implements MerchantAuthorizationGateway {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public MerchantAuthorizationGatewayImpl(JdbcTemplate jdbc, @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static MerchantRelationship mapRelationship(ResultSet rs, int index) throws SQLException {
        return new MerchantRelationship(rs.getString("id"), rs.getString("parent_merchant_id"),
                rs.getString("child_merchant_id"), GrantState.valueOf(rs.getString("state")),
                rs.getObject("valid_from", LocalDateTime.class), rs.getObject("valid_to", LocalDateTime.class),
                rs.getLong("row_version"));
    }

    private static MerchantActionGrant mapGrant(ResultSet rs, int index) throws SQLException {
        return new MerchantActionGrant(rs.getString("id"), PrincipalType.valueOf(rs.getString("principal_type")),
                rs.getString("principal_id"), rs.getString("payee_merchant_id"), rs.getString("relationship_id"),
                MerchantAction.valueOf(rs.getString("action")), GrantState.valueOf(rs.getString("state")),
                rs.getObject("valid_from", LocalDateTime.class), rs.getObject("valid_to", LocalDateTime.class),
                rs.getLong("row_version"));
    }

    @Override
    public Optional<MerchantRelationship> findRelationship(String tenant, String id) {
        return relationship("SELECT * FROM merchant_relationship WHERE tenant_id = ? AND id = ? AND deleted = FALSE", tenant, id);
    }

    @Override
    public Optional<MerchantRelationship> findLiveRelationship(String tenant, String parent, String child) {
        return relationship("""
                SELECT * FROM merchant_relationship WHERE tenant_id = ? AND parent_merchant_id = ?
                    AND child_merchant_id = ? AND relation_type = 'MANAGEMENT' AND state <> 'REVOKED' AND deleted = FALSE
                """, tenant, parent, child);
    }

    @Override
    public MerchantRelationship lockRelationship(String tenant, String id) {
        writable(tenant);
        return relationship("SELECT * FROM merchant_relationship WHERE tenant_id = ? AND id = ? AND deleted = FALSE FOR UPDATE", tenant, id)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }

    @Override
    public Optional<MerchantActionGrant> findGrantById(String tenant, String id) {
        return grant("SELECT * FROM merchant_action_grant WHERE tenant_id = ? AND id = ? AND deleted = FALSE", tenant, id);
    }

    @Override
    public MerchantActionGrant lockGrant(String tenant, String id) {
        writable(tenant);
        return grant("SELECT * FROM merchant_action_grant WHERE tenant_id = ? AND id = ? AND deleted = FALSE FOR UPDATE", tenant, id)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }

    @Override
    public Optional<MerchantActionGrant> findGrant(String tenant, PrincipalType type, String principal, String payee, MerchantAction action) {
        return grant("""
                SELECT * FROM merchant_action_grant WHERE tenant_id = ? AND principal_type = ? AND principal_id = ?
                    AND payee_merchant_id = ? AND action = ? AND state <> 'REVOKED' AND deleted = FALSE
                """, tenant, type.name(), principal, payee, action.name());
    }

    @Override
    public void saveRelationship(String tenant, MerchantRelationship model, long expected) {
        writable(tenant);
        if (model == null || (expected == -1 ? model.version() != 0
                : expected < 0 || expected == Long.MAX_VALUE || model.version() != expected + 1))
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        LocalDateTime now = LocalDateTime.now(clock);
        int changed;
        if (expected == -1) {
            changed = jdbc.update("""
                            INSERT INTO merchant_relationship (id, tenant_id, created_at, updated_at, deleted,
                                parent_merchant_id, child_merchant_id, relation_type, state, valid_from, valid_to, row_version)
                            VALUES (?, ?, ?, ?, FALSE, ?, ?, 'MANAGEMENT', ?, ?, ?, ?)
                            ON CONFLICT (tenant_id, parent_merchant_id, child_merchant_id, relation_type)
                                WHERE state IN ('INIT', 'ACTIVE') DO NOTHING
                            """, model.id(), tenant, now, now, model.parentMerchantId(), model.childMerchantId(),
                    model.state().name(), model.validFrom(), model.validTo(), model.version());
            if (changed == 0) throw new CommonException(CustomerResultCodeEnum.RELATIONSHIP_CONFLICT);
        } else {
            changed = jdbc.update("""
                            UPDATE merchant_relationship SET parent_merchant_id = ?, child_merchant_id = ?, state = ?,
                                valid_from = ?, valid_to = ?, row_version = ?, updated_at = ?
                            WHERE tenant_id = ? AND id = ? AND row_version = ? AND deleted = FALSE
                            """, model.parentMerchantId(), model.childMerchantId(), model.state().name(), model.validFrom(),
                    model.validTo(), model.version(), now, tenant, model.id(), expected);
            if (changed == 0) throw new CommonException(CustomerResultCodeEnum.CONFLICT);
        }
        if (changed != 1) throw new IllegalStateException("Unexpected relationship write count");
    }

    @Override
    public void saveGrant(String tenant, MerchantActionGrant model, long expected) {
        writable(tenant);
        if (model == null || (expected == -1 ? model.version() != 0
                : expected < 0 || expected == Long.MAX_VALUE || model.version() != expected + 1))
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        LocalDateTime now = LocalDateTime.now(clock);
        int changed;
        if (expected == -1) {
            changed = jdbc.update("""
                            INSERT INTO merchant_action_grant (id, tenant_id, created_at, updated_at, deleted, principal_type,
                                principal_id, payee_merchant_id, relationship_id, action, state, valid_from, valid_to, row_version)
                            VALUES (?, ?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (tenant_id, principal_type, principal_id, payee_merchant_id, action)
                                WHERE state IN ('INIT', 'ACTIVE') DO NOTHING
                            """, model.id(), tenant, now, now, model.principalType().name(), model.principalId(),
                    model.payeeMerchantId(), model.relationshipId(), model.action().name(), model.state().name(),
                    model.validFrom(), model.validTo(), model.version());
            if (changed == 0) throw new CommonException(CustomerResultCodeEnum.GRANT_CONFLICT);
        } else {
            changed = jdbc.update("""
                            UPDATE merchant_action_grant SET principal_type = ?, principal_id = ?, payee_merchant_id = ?,
                                relationship_id = ?, action = ?, state = ?, valid_from = ?, valid_to = ?, row_version = ?, updated_at = ?
                            WHERE tenant_id = ? AND id = ? AND row_version = ? AND deleted = FALSE
                            """, model.principalType().name(), model.principalId(), model.payeeMerchantId(), model.relationshipId(),
                    model.action().name(), model.state().name(), model.validFrom(), model.validTo(), model.version(), now,
                    tenant, model.id(), expected);
            if (changed == 0) throw new CommonException(CustomerResultCodeEnum.CONFLICT);
        }
        if (changed != 1) throw new IllegalStateException("Unexpected grant write count");
    }

    private static void tenant(String tenant) {
        if (tenant == null || !tenant.equals(io.github.loadup.commons.util.TenantUtil.getTenantId()))
            throw new CommonException(CustomerResultCodeEnum.ACTION_FORBIDDEN);
    }

    private void writable(String tenant) {
        tenant(tenant);
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        var source = jdbc.getDataSource();
        if (source == null || !org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(source))
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
    }

    private Optional<MerchantRelationship> relationship(String sql, Object... parameters) {
        tenant((String) parameters[0]);
        return jdbc.query(sql, MerchantAuthorizationGatewayImpl::mapRelationship, parameters).stream().findFirst();
    }

    private Optional<MerchantActionGrant> grant(String sql, Object... parameters) {
        tenant((String) parameters[0]);
        return jdbc.query(sql, MerchantAuthorizationGatewayImpl::mapGrant, parameters).stream().findFirst();
    }
}
