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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.app.port.RoutingProfileCodec;
import io.github.loadup.pay.payment.domain.gateway.RoutingProfileGateway;
import io.github.loadup.pay.payment.domain.model.RoutingProfile;
import io.github.loadup.pay.payment.infrastructure.converter.RoutingProfileStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RoutingProfileDO;
import io.github.loadup.components.database.tenant.TenantContext;
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

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RoutingProfileGatewayImpl implements RoutingProfileGateway {
    private static final String COLUMNS = "id, tenant_id, created_at, updated_at, profile_ref, payee_merchant_id, "
            + "spec_payload, content_hash, caller_service_id, request_key, request_digest, published_at";
    private final NamedParameterJdbcTemplate jdbc;
    private final RoutingProfileStorageConverter converter;
    private final RoutingProfileCodec codec;
    private final Clock clock;

    public RoutingProfileGatewayImpl(NamedParameterJdbcTemplate jdbc, RoutingProfileStorageConverter converter,
                                     RoutingProfileCodec codec, @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.codec = codec;
        this.clock = clock;
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    @Override
    public Optional<RoutingProfile> find(String tenant, String ref) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("ref", ref);
        var row = jdbc.query("SELECT " + COLUMNS + " FROM routing_profile WHERE tenant_id = :tenant "
                        + "AND profile_ref = :ref AND deleted = FALSE", args,
                BeanPropertyRowMapper.newInstance(RoutingProfileDO.class)).stream().findFirst().orElse(null);
        return decode(row);
    }

    @Override
    public Optional<RoutingProfile> findRequest(String tenant, String caller, String key) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("caller", caller).addValue("key", key);
        var row = jdbc.query("SELECT " + COLUMNS + " FROM routing_profile WHERE tenant_id = :tenant "
                        + "AND caller_service_id = :caller AND request_key = :key AND deleted = FALSE", args,
                BeanPropertyRowMapper.newInstance(RoutingProfileDO.class)).stream().findFirst().orElse(null);
        return decode(row);
    }

    private Optional<RoutingProfile> decode(RoutingProfileDO row) {
        if (row == null) return Optional.empty();
        try {
            var spec = codec.decode(row.getSpecPayload(), row.getContentHash());
            if (!spec.profileRef().equals(row.getProfileRef()) || !spec.payeeMerchantId().equals(row.getPayeeMerchantId())
                    || !codec.requestDigest(row.getTenantId(), row.getCallerServiceId(), row.getRequestKey(),
                    row.getContentHash()).equals(row.getRequestDigest())) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toDomain(row, spec));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, RoutingProfile profile) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        String payload = codec.encode(profile.spec());
        if (!codec.checksum(payload).equals(profile.contentHash())
                || !codec.requestDigest(tenant, profile.callerServiceId(), profile.requestKey(), profile.contentHash())
                .equals(profile.requestDigest())) throw new CommonException(SNAPSHOT_INVALID);
        var row = converter.toDO(tenant, profile, payload);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int inserted = jdbc.update("""
                INSERT INTO routing_profile (id, tenant_id, created_at, updated_at, deleted, profile_ref,
                    payee_merchant_id, spec_payload, content_hash, caller_service_id, request_key, request_digest, published_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :profileRef, :payeeMerchantId, :specPayload,
                    :contentHash, :callerServiceId, :requestKey, :requestDigest, :publishedAt)
                ON CONFLICT (tenant_id, caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (inserted == 0) throw new PaymentWriteConflictException("Routing profile request key already exists");
        if (inserted != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
