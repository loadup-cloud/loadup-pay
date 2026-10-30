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
package io.github.loadup.pay.product.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import io.github.loadup.pay.product.domain.gateway.PolicyPublicationGateway;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import io.github.loadup.pay.product.infrastructure.converter.PolicyPublicationStorageConverter;
import io.github.loadup.pay.product.infrastructure.dataobject.ContractLimitPublicationDO;
import io.github.loadup.pay.product.infrastructure.exception.PublicationKeyConflictException;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Repository
public class PolicyPublicationGatewayImpl implements PolicyPublicationGateway {
    public static final String COLUMNS = "id, tenant_id, created_at, updated_at, contract_id, contract_revision, "
            + "policy_id, policy_version, policy_hash, state, activated_at, row_version, payee_merchant_id, "
            + "product_scope_key, snapshot_hash, activation_payload, outbox_event_id, requested_at, publication_schema_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final ContractLimitPublicationRepository publications;
    private final PolicyPublicationStorageConverter converter;
    private final Clock clock;
    private final BeanPropertyRowMapper<ContractLimitPublicationDO> mapper = BeanPropertyRowMapper.newInstance(ContractLimitPublicationDO.class);

    public PolicyPublicationGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            ContractLimitPublicationRepository publications,
            PolicyPublicationStorageConverter converter,
            @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.publications = publications;
        this.converter = converter;
        this.clock = clock;
    }

    private static boolean businessCollision(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException sql && "23505".equals(sql.getSQLState()) && sql.getServerErrorMessage() != null
                    && sql.getServerErrorMessage().getConstraint() != null)
                return Set.of("uk_contract_policy_publication", "uk_policy_publication_version", "uk_initial_policy_merchant")
                        .contains(sql.getServerErrorMessage().getConstraint());
        }
        return false;
    }

    private Optional<PolicyPublication> locked(String id) {
        var tenant = TenantContext.tenantId();
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id);
        try {
            return jdbc.query("SELECT " + COLUMNS + " FROM contract_limit_publication WHERE tenant_id = :tenant "
                    + "AND deleted = FALSE AND id = :id FOR UPDATE", args, mapper).stream().findFirst().map(row -> {
                if (!tenant.equals(row.getTenantId()))
                    throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
                return converter.toModel(row);
            });
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<PolicyPublication> findById(String tenant, String id) {
        return read(() -> publications.findPublication(id));
    }

    @Override
    public Optional<PolicyPublication> lockById(String tenant, String id) {
        return locked(id);
    }

    @Override
    public Optional<PolicyPublication> findByContract(String tenant, String contract, int revision) {
        return read(() -> publications.findPublished(contract, revision));
    }

    @Override
    public Optional<PolicyPublication> findByMerchant(String tenant, String merchant) {
        return read(() -> publications.findActiveByMerchant(merchant));
    }

    @Override
    public Optional<PolicyPublication> findByPolicy(String tenant, String policy) {
        return read(() -> publications.findActiveByPolicy(policy));
    }

    private Optional<PolicyPublication> read(
            java.util.function.Supplier<Optional<ContractLimitPublicationDO>> lookup) {
        try {
            return lookup.get().map(this::map);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
        }
    }

    private PolicyPublication map(ContractLimitPublicationDO row) {
        var tenant = TenantContext.tenantId();
        if (!tenant.equals(row.getTenantId())) throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
        return converter.toModel(row);
    }

    @Override
    public void insert(String tenant, PolicyPublication value) {
        ContractLimitPublicationDO row = converter.toDO(tenant, value);
        LocalDateTime now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            int changed = jdbc.update("""
                    INSERT INTO contract_limit_publication (id, tenant_id, created_at, updated_at, deleted, contract_id,
                        contract_revision, policy_id, policy_version, policy_hash, state, activated_at, row_version,
                        payee_merchant_id, product_scope_key, snapshot_hash, activation_payload, outbox_event_id,
                        requested_at, publication_schema_version)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :contractId, :contractRevision, :policyId,
                        :policyVersion, :policyHash, :state, :activatedAt, :rowVersion, :payeeMerchantId, :productScopeKey,
                        :snapshotHash, :activationPayload, :outboxEventId, :requestedAt, :publicationSchemaVersion)
                    """, new BeanPropertySqlParameterSource(row));
            if (changed != 1) throw new IllegalStateException("Unexpected publication insert count");
        } catch (DuplicateKeyException failure) {
            if (businessCollision(failure)) throw new PublicationKeyConflictException(failure);
            throw failure;
        }
    }

    @Override
    public void update(String tenant, PolicyPublication value, long expectedVersion) {
        ContractLimitPublicationDO row = converter.toDO(tenant, value);
        row.setUpdatedAt(LocalDateTime.now(clock));
        var source = new BeanPropertySqlParameterSource(row);
        var args = new MapSqlParameterSource();
        for (String name : source.getReadablePropertyNames()) args.addValue(name, source.getValue(name));
        args.addValue("expected", expectedVersion);
        int changed = jdbc.update("""
                UPDATE contract_limit_publication SET contract_id = :contractId, contract_revision = :contractRevision,
                    policy_id = :policyId, policy_version = :policyVersion, policy_hash = :policyHash, state = :state,
                    activated_at = :activatedAt, row_version = :rowVersion, payee_merchant_id = :payeeMerchantId,
                    product_scope_key = :productScopeKey, snapshot_hash = :snapshotHash, activation_payload = :activationPayload,
                    outbox_event_id = :outboxEventId, requested_at = :requestedAt,
                    publication_schema_version = :publicationSchemaVersion, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :id AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT);
    }
}
