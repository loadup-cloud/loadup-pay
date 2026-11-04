/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.merchantcenter.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.merchantcenter.client.enums.GrantState;
import io.github.loadup.merchantcenter.client.enums.MerchantAction;
import io.github.loadup.merchantcenter.client.enums.PrincipalType;
import io.github.loadup.merchantcenter.client.error.CustomerResultCodeEnum;
import io.github.loadup.merchantcenter.domain.gateway.MerchantAuthorizationGateway;
import io.github.loadup.merchantcenter.domain.model.MerchantActionGrant;
import io.github.loadup.merchantcenter.domain.model.MerchantRelationship;
import io.github.loadup.merchantcenter.infrastructure.dataobject.MerchantActionGrantDO;
import io.github.loadup.merchantcenter.infrastructure.dataobject.MerchantRelationshipDO;
import io.github.loadup.merchantcenter.infrastructure.mapper.MerchantActionGrantMapper;
import io.github.loadup.merchantcenter.infrastructure.mapper.MerchantRelationshipMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * All callers acquire the payee merchant row before modifying authorization records.
 */
@Repository
public class MerchantAuthorizationGatewayImpl extends ServiceImpl<MerchantRelationshipMapper, MerchantRelationshipDO>
        implements MerchantAuthorizationGateway {
    private static final List<String> LIVE_STATES = List.of("INIT", "ACTIVE");

    private final JdbcTemplate jdbc;
    private final MerchantActionGrantMapper grants;
    private final Clock clock;

    public MerchantAuthorizationGatewayImpl(
            JdbcTemplate jdbc, MerchantRelationshipMapper mapper, MerchantActionGrantMapper grants, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.grants = grants;
        this.clock = clock;
    }

    private static MerchantRelationship mapRelationship(ResultSet rs, int index) throws SQLException {
        return new MerchantRelationship(
                rs.getString("id"),
                rs.getString("parent_merchant_id"),
                rs.getString("child_merchant_id"),
                GrantState.valueOf(rs.getString("state")),
                rs.getObject("valid_from", LocalDateTime.class),
                rs.getObject("valid_to", LocalDateTime.class),
                rs.getLong("row_version"));
    }

    private static MerchantActionGrant mapGrant(ResultSet rs, int index) throws SQLException {
        return new MerchantActionGrant(
                rs.getString("id"),
                PrincipalType.valueOf(rs.getString("principal_type")),
                rs.getString("principal_id"),
                rs.getString("payee_merchant_id"),
                rs.getString("relationship_id"),
                MerchantAction.valueOf(rs.getString("action")),
                GrantState.valueOf(rs.getString("state")),
                rs.getObject("valid_from", LocalDateTime.class),
                rs.getObject("valid_to", LocalDateTime.class),
                rs.getLong("row_version"));
    }

    private static boolean initialVersion(GrantState state, long version) {
        return state == GrantState.INIT && version == 0 || state == GrantState.ACTIVE && version == 1;
    }

    private static MerchantRelationship mapRelationship(MerchantRelationshipDO row) {
        return new MerchantRelationship(
                row.getId(),
                row.getParentMerchantId(),
                row.getChildMerchantId(),
                GrantState.valueOf(row.getState()),
                row.getValidFrom(),
                row.getValidTo(),
                row.getRowVersion());
    }

    private static MerchantActionGrant mapGrant(MerchantActionGrantDO row) {
        return new MerchantActionGrant(
                row.getId(),
                PrincipalType.valueOf(row.getPrincipalType()),
                row.getPrincipalId(),
                row.getPayeeMerchantId(),
                row.getRelationshipId(),
                MerchantAction.valueOf(row.getAction()),
                GrantState.valueOf(row.getState()),
                row.getValidFrom(),
                row.getValidTo(),
                row.getRowVersion());
    }

    @Override
    public Optional<MerchantRelationship> findRelationship(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getById(id)).map(MerchantAuthorizationGatewayImpl::mapRelationship);
    }

    @Override
    public Optional<MerchantRelationship> findLiveRelationship(String parent, String child) {
        if (parent == null || child == null) {
            return Optional.empty();
        }
        var query = QueryWrapper.create()
                .eq("parent_merchant_id", parent)
                .eq("child_merchant_id", child)
                .eq("relation_type", "MANAGEMENT")
                .in("state", LIVE_STATES);
        return Optional.ofNullable(getOne(query)).map(MerchantAuthorizationGatewayImpl::mapRelationship);
    }

    @Override
    public MerchantRelationship lockRelationship(String id) {
        writable();
        return relationship("SELECT * FROM merchant_relationship WHERE id = ? AND deleted = FALSE FOR UPDATE", id)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }

    @Override
    public Optional<MerchantActionGrant> findGrantById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(grants.selectOneById(id)).map(MerchantAuthorizationGatewayImpl::mapGrant);
    }

    @Override
    public MerchantActionGrant lockGrant(String id) {
        writable();
        return grant("SELECT * FROM merchant_action_grant WHERE id = ? AND deleted = FALSE FOR UPDATE", id)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }

    @Override
    public Optional<MerchantActionGrant> findGrant(
            PrincipalType type, String principal, String payee, MerchantAction action) {
        if (principal == null || payee == null) {
            return Optional.empty();
        }
        var query = QueryWrapper.create()
                .eq("principal_type", type.name())
                .eq("principal_id", principal)
                .eq("payee_merchant_id", payee)
                .eq("action", action.name())
                .in("state", LIVE_STATES);
        return Optional.ofNullable(grants.selectOneByQuery(query)).map(MerchantAuthorizationGatewayImpl::mapGrant);
    }

    @Override
    public void saveRelationship(MerchantRelationship model, long expected) {
        writable();
        if (model == null
                || (expected == -1
                        ? !initialVersion(model.state(), model.version())
                        : expected < 0 || expected == Long.MAX_VALUE || model.version() != expected + 1)) {
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int changed;
        if (expected == -1) {
            changed = jdbc.update(
                    """
                            INSERT INTO merchant_relationship (id, created_at, updated_at, deleted,
                                parent_merchant_id, child_merchant_id, relation_type, state, valid_from, valid_to, row_version)
                            VALUES (?, ?, ?, FALSE, ?, ?, 'MANAGEMENT', ?, ?, ?, ?)
                            ON CONFLICT (parent_merchant_id, child_merchant_id, relation_type)
                                WHERE state IN ('INIT', 'ACTIVE') DO NOTHING
                            """,
                    model.id(),
                    now,
                    now,
                    model.parentMerchantId(),
                    model.childMerchantId(),
                    model.state().name(),
                    model.validFrom(),
                    model.validTo(),
                    model.version());
            if (changed == 0) {
                throw new CommonException(CustomerResultCodeEnum.RELATIONSHIP_CONFLICT);
            }
        } else {
            changed = jdbc.update(
                    """
                            UPDATE merchant_relationship SET state = ?, row_version = ?, updated_at = ?
                            WHERE id = ? AND row_version = ? AND deleted = FALSE
                                AND parent_merchant_id = ? AND child_merchant_id = ?
                                AND valid_from = ? AND valid_to IS NOT DISTINCT FROM ?
                                AND ((state = 'INIT' AND ? IN ('ACTIVE', 'REVOKED'))
                                    OR (state = 'ACTIVE' AND ? = 'REVOKED'))
                            """,
                    model.state().name(),
                    model.version(),
                    now,
                    model.id(),
                    expected,
                    model.parentMerchantId(),
                    model.childMerchantId(),
                    model.validFrom(),
                    model.validTo(),
                    model.state().name(),
                    model.state().name());
            if (changed == 0) {
                throw new CommonException(CustomerResultCodeEnum.CONFLICT);
            }
        }
        if (changed != 1) {
            throw new IllegalStateException("Unexpected relationship write count");
        }
    }

    @Override
    public void saveGrant(MerchantActionGrant model, long expected) {
        writable();
        if (model == null
                || (expected == -1
                        ? !initialVersion(model.state(), model.version())
                        : expected < 0 || expected == Long.MAX_VALUE || model.version() != expected + 1)) {
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int changed;
        if (expected == -1) {
            changed = jdbc.update(
                    """
                            INSERT INTO merchant_action_grant (id, created_at, updated_at, deleted, principal_type,
                                principal_id, payee_merchant_id, relationship_id, action, state, valid_from, valid_to, row_version)
                            VALUES (?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (principal_type, principal_id, payee_merchant_id, action)
                                WHERE state IN ('INIT', 'ACTIVE') DO NOTHING
                            """,
                    model.id(),
                    now,
                    now,
                    model.principalType().name(),
                    model.principalId(),
                    model.payeeMerchantId(),
                    model.relationshipId(),
                    model.action().name(),
                    model.state().name(),
                    model.validFrom(),
                    model.validTo(),
                    model.version());
            if (changed == 0) {
                throw new CommonException(CustomerResultCodeEnum.GRANT_CONFLICT);
            }
        } else {
            changed = jdbc.update(
                    """
                            UPDATE merchant_action_grant SET state = ?, row_version = ?, updated_at = ?
                            WHERE id = ? AND row_version = ? AND deleted = FALSE
                                AND principal_type = ? AND principal_id = ? AND payee_merchant_id = ?
                                AND relationship_id IS NOT DISTINCT FROM ? AND action = ?
                                AND valid_from = ? AND valid_to IS NOT DISTINCT FROM ?
                                AND ((state = 'INIT' AND ? IN ('ACTIVE', 'REVOKED'))
                                    OR (state = 'ACTIVE' AND ? = 'REVOKED'))
                            """,
                    model.state().name(),
                    model.version(),
                    now,
                    model.id(),
                    expected,
                    model.principalType().name(),
                    model.principalId(),
                    model.payeeMerchantId(),
                    model.relationshipId(),
                    model.action().name(),
                    model.validFrom(),
                    model.validTo(),
                    model.state().name(),
                    model.state().name());
            if (changed == 0) {
                throw new CommonException(CustomerResultCodeEnum.CONFLICT);
            }
        }
        if (changed != 1) {
            throw new IllegalStateException("Unexpected grant write count");
        }
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        }
        var source = jdbc.getDataSource();
        if (source == null || !TransactionSynchronizationManager.hasResource(source)) {
            throw new CommonException(CustomerResultCodeEnum.INVALID_STATE);
        }
    }

    private Optional<MerchantRelationship> relationship(String sql, Object... parameters) {
        return jdbc
                .query(
                        sql,
                        (RowMapper<MerchantRelationship>) MerchantAuthorizationGatewayImpl::mapRelationship,
                        parameters)
                .stream()
                .findFirst();
    }

    private Optional<MerchantActionGrant> grant(String sql, Object... parameters) {
        return jdbc
                .query(sql, (RowMapper<MerchantActionGrant>) MerchantAuthorizationGatewayImpl::mapGrant, parameters)
                .stream()
                .findFirst();
    }
}
