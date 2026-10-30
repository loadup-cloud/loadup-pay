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

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import io.github.loadup.pay.product.domain.gateway.OrderAdmissionGateway;
import io.github.loadup.pay.product.domain.model.StoredOrderAdmission;
import io.github.loadup.pay.product.infrastructure.converter.AdmissionStorageConverter;
import io.github.loadup.pay.product.infrastructure.dataobject.OrderAdmissionDO;
import io.github.loadup.pay.product.infrastructure.exception.AdmissionOrderConflictException;
import io.github.loadup.pay.product.infrastructure.mapper.OrderAdmissionMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class OrderAdmissionGatewayImpl extends ServiceImpl<OrderAdmissionMapper, OrderAdmissionDO>
        implements OrderAdmissionGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final AdmissionStorageConverter converter;
    private final Clock clock;

    public OrderAdmissionGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            OrderAdmissionMapper mapper,
            AdmissionStorageConverter converter,
            Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<StoredOrderAdmission> findByOrder(String tenant, String order) {
        return read(() -> Optional.ofNullable(
                getOne(QueryWrapper.create().eq("order_id", order))));
    }

    @Override
    public Optional<StoredOrderAdmission> findById(String tenant, String id) {
        return read(() -> Optional.ofNullable(getById(id)));
    }

    private Optional<StoredOrderAdmission> read(
            java.util.function.Supplier<Optional<OrderAdmissionDO>> lookup) {
        try {
            return lookup.get().map(converter::toStored);
        } catch (IllegalArgumentException | NullPointerException corrupted) {
            throw new CommonException(ProductResultCodeEnum.SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, StoredOrderAdmission value) {
        OrderAdmissionDO row = converter.toDO(value);
        LocalDateTime now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_admission (id, created_at, updated_at, deleted, order_id, payee_merchant_id,
                    product_scope_key, request_digest, amount_minor, currency, requested_due_at, caller_scope_hash,
                    contract_id, contract_revision, contract_generation, merchant_fact_version, authorization_version,
                    state, reason_code, admitted_at, materialize_before, snapshot_hash, compiled_terms_hash, compiled_terms)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId, :productScopeKey,
                    :requestDigest, :amountMinor, :currency, :requestedDueAt, :callerScopeHash, :contractId,
                    :contractRevision, :contractGeneration, :merchantFactVersion, :authorizationVersion, :state,
                    :reasonCode, :admittedAt, :materializeBefore, :snapshotHash, :compiledTermsHash, :compiledTerms)
                ON CONFLICT (order_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AdmissionOrderConflictException();
        }
        if (count != 1) {
            throw new IllegalStateException("Unexpected admission insert count");
        }
    }
}
