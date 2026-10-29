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
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import io.github.loadup.pay.product.domain.gateway.OrderAdmissionGateway;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import io.github.loadup.pay.product.infrastructure.converter.AdmissionStorageConverter;
import io.github.loadup.pay.product.infrastructure.dataobject.OrderAdmissionDO;
import io.github.loadup.pay.product.infrastructure.exception.AdmissionOrderConflictException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class OrderAdmissionGatewayImpl implements OrderAdmissionGateway {
    private static final String COLUMNS = "id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, "
            + "product_scope_key, request_digest, amount_minor, currency, requested_due_at, caller_scope_hash, "
            + "contract_id, contract_revision, contract_generation, merchant_fact_version, authorization_version, "
            + "state, reason_code, admitted_at, materialize_before, snapshot_hash, compiled_terms_hash, compiled_terms";
    private final NamedParameterJdbcTemplate jdbc;
    private final AdmissionStorageConverter converter;
    private final Clock clock;
    private final BeanPropertyRowMapper<OrderAdmissionDO> rowMapper = BeanPropertyRowMapper.newInstance(OrderAdmissionDO.class);

    public OrderAdmissionGatewayImpl(NamedParameterJdbcTemplate jdbc, AdmissionStorageConverter converter,
                                     @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<StoredOrderAdmission> findByOrder(String tenant, String order) {
        return read("order_id = :value", tenant, order);
    }

    @Override
    public Optional<StoredOrderAdmission> findById(String tenant, String id) {
        return read("id = :value", tenant, id);
    }

    private Optional<StoredOrderAdmission> read(String condition, String tenant, String value) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("value", value);
        try {
            return jdbc.query("SELECT " + COLUMNS + " FROM order_admission WHERE tenant_id = :tenant "
                    + "AND deleted = FALSE AND " + condition, args, rowMapper).stream().findFirst().map(converter::toStored);
        } catch (IllegalArgumentException | NullPointerException corrupted) {
            throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredOrderAdmission value) {
        OrderAdmissionDO row = converter.toDO(tenant, value);
        LocalDateTime now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_admission (id, tenant_id, created_at, updated_at, deleted, order_id, payee_merchant_id,
                    product_scope_key, request_digest, amount_minor, currency, requested_due_at, caller_scope_hash,
                    contract_id, contract_revision, contract_generation, merchant_fact_version, authorization_version,
                    state, reason_code, admitted_at, materialize_before, snapshot_hash, compiled_terms_hash, compiled_terms)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId, :productScopeKey,
                    :requestDigest, :amountMinor, :currency, :requestedDueAt, :callerScopeHash, :contractId,
                    :contractRevision, :contractGeneration, :merchantFactVersion, :authorizationVersion, :state,
                    :reasonCode, :admittedAt, :materializeBefore, :snapshotHash, :compiledTermsHash, :compiledTerms)
                ON CONFLICT (tenant_id, order_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new AdmissionOrderConflictException();
        if (count != 1) throw new IllegalStateException("Unexpected admission insert count");
    }
}
