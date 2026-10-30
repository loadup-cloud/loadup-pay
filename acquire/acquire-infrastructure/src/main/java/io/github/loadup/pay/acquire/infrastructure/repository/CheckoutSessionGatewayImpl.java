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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionScope;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import io.github.loadup.pay.acquire.infrastructure.converter.CheckoutStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.CheckoutSessionDO;
import io.github.loadup.components.database.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class CheckoutSessionGatewayImpl implements CheckoutSessionGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final CheckoutStorageConverter converter;
    private final JsonMapper json;

    public CheckoutSessionGatewayImpl(
            NamedParameterJdbcTemplate jdbc, CheckoutStorageConverter converter, JsonMapper json, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.json = json.rebuild()
                .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private Optional<StoredCheckoutSession> query(String predicate, MapSqlParameterSource args, boolean lock) {
        var rows = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, request_key, request_digest,
                       token_hash, token_nonce, token_key_ref, token_key_version, state, expires_at, allowed_origin,
                       scope_snapshot, row_version, revoked_at
                FROM checkout_session WHERE tenant_id = :tenant AND deleted = FALSE AND
                """ + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(CheckoutSessionDO.class));
        return stored(rows.stream().findFirst().orElse(null));
    }

    @Override
    public Optional<StoredCheckoutSession> find(String tenant, String id) {
        scope(tenant);
        return query("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), false);
    }

    @Override
    public Optional<StoredCheckoutSession> lock(String tenant, String id) {
        scope(tenant);
        writable();
        return query("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), true);
    }

    @Override
    public Optional<StoredCheckoutSession> findRequest(String tenant, String order, String key) {
        scope(tenant);
        return query("order_id = :order AND request_key = :key", new MapSqlParameterSource("tenant", TenantContext.tenantId())
                .addValue("order", order).addValue("key", key), false);
    }

    private Optional<StoredCheckoutSession> stored(CheckoutSessionDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getScopeSnapshot() == null || row.getScopeSnapshot().length() > 8192)
                throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(
                    converter.toStored(row, json.readValue(row.getScopeSnapshot(), CheckoutSessionScope.class)));
        } catch (RuntimeException corrupted) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredCheckoutSession value) {
        scope(tenant);
        writable();
        var payload = json.writeValueAsString(value.scope());
        if (payload.length() > 8192) throw new CommonException(SNAPSHOT_INVALID);
        var row = converter.toDO(tenant, value, payload);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO checkout_session (id, tenant_id, created_at, updated_at, deleted, order_id, payee_merchant_id,
                    request_key, request_digest, token_hash, token_nonce, token_key_ref, token_key_version, state, expires_at,
                    allowed_origin, scope_snapshot, row_version, revoked_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId, :requestKey,
                    :requestDigest, :tokenHash, :tokenNonce, :tokenKeyRef, :tokenKeyVersion, :state, :expiresAt,
                    :allowedOrigin, :scopeSnapshot, :rowVersion, :revokedAt)
                ON CONFLICT (tenant_id, order_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new AcquireWriteConflictException("Checkout session request already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void revoke(String tenant, StoredCheckoutSession before, StoredCheckoutSession after) {
        scope(tenant);
        writable();
        if (before == null || after == null || before.revokedAt() != null || after.revokedAt() == null
                || !before.revoke(before.rowVersion(), after.revokedAt()).equals(after))
            throw new CommonException(SNAPSHOT_INVALID);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", before.session().id())
                .addValue("expected", before.rowVersion()).addValue("version", after.rowVersion())
                .addValue("revokedAt", after.revokedAt()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE checkout_session SET revoked_at = :revokedAt, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND revoked_at IS NULL AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
