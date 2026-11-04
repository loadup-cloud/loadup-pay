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

import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.merchantcenter.client.error.MerchantResultCodeEnum;
import io.github.loadup.merchantcenter.domain.gateway.MerchantGateway;
import io.github.loadup.merchantcenter.domain.model.Merchant;
import io.github.loadup.merchantcenter.domain.model.MerchantStatus;
import io.github.loadup.merchantcenter.infrastructure.converter.MerchantStorageConverter;
import io.github.loadup.merchantcenter.infrastructure.dataobject.MerchantDO;
import io.github.loadup.merchantcenter.infrastructure.mapper.MerchantMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class MerchantGatewayImpl extends ServiceImpl<MerchantMapper, MerchantDO> implements MerchantGateway {
    private static final String COLUMNS = "id, merchant_code, name, short_name, type, industry, country, "
            + "province, city, address, registration_no, contact_name, contact_phone, contact_email, status, "
            + "row_version, created_by, updated_by, created_at, updated_at";
    private final NamedParameterJdbcTemplate jdbc;
    private final MerchantStorageConverter converter;
    private final Clock clock;
    private final BeanPropertyRowMapper<MerchantDO> rowMapper = BeanPropertyRowMapper.newInstance(MerchantDO.class);

    public MerchantGatewayImpl(
            NamedParameterJdbcTemplate jdbc, MerchantMapper mapper, MerchantStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.converter = converter;
        this.clock = clock;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new CommonException(MerchantResultCodeEnum.ID_REQUIRED);
        }
        return value;
    }

    @Override
    public Optional<Merchant> find(String id) {
        return getByIdOpt(required(id)).map(converter::toMerchant);
    }

    @Override
    public Optional<Merchant> lock(String id) {
        var dataSource = jdbc.getJdbcTemplate().getDataSource();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || dataSource == null
                || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Merchant lock requires a writable transaction on its data source");
        }
        return read(id, true);
    }

    private Optional<Merchant> read(String id, boolean lock) {
        var params = new MapSqlParameterSource().addValue("id", required(id));
        return jdbc
                .query(
                        "SELECT " + COLUMNS + " FROM merchant_profile WHERE id = :id " + "AND deleted = FALSE"
                                + (lock ? " FOR UPDATE" : ""),
                        params,
                        rowMapper)
                .stream()
                .findFirst()
                .map(converter::toMerchant);
    }

    @Override
    public void insert(Merchant merchant) {
        MerchantDO row = converter.toDO(merchant);
        required(row.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO merchant_profile (id, merchant_code, name, short_name, type, industry, country,
                    province, city, address, registration_no, contact_name, contact_phone, contact_email, status,
                    row_version, created_by, updated_by, created_at, updated_at, deleted)
                VALUES (:id, :merchantCode, :name, :shortName, :type, :industry, :country, :province,
                    :city, :address, :registrationNo, :contactName, :contactPhone, :contactEmail, :status,
                    :rowVersion, :createdBy, :updatedBy, :createdAt, :updatedAt, FALSE)
                ON CONFLICT (merchant_code) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new CommonException(MerchantResultCodeEnum.CONFLICT);
        }
        if (count != 1) {
            throw new IllegalStateException("Unexpected merchant insert count");
        }
    }

    @Override
    public void update(Merchant merchant, long expected) {
        if (expected < 1 || expected == Long.MAX_VALUE || merchant.rowVersion() != expected + 1) {
            throw new CommonException(MerchantResultCodeEnum.CONFLICT);
        }
        MerchantDO row = converter.toDO(merchant);
        required(row.getId());
        row.setUpdatedAt(LocalDateTime.now(clock));
        var source = new BeanPropertySqlParameterSource(row);
        var params = new MapSqlParameterSource();
        for (String name : source.getReadablePropertyNames()) {
            params.addValue(name, source.getValue(name));
        }
        params.addValue("expected", expected);
        int changed = jdbc.update("""
                UPDATE merchant_profile SET name = :name, short_name = :shortName,
                    type = :type, industry = :industry, country = :country, province = :province, city = :city,
                    address = :address, registration_no = :registrationNo, contact_name = :contactName,
                    contact_phone = :contactPhone, contact_email = :contactEmail, status = :status,
                    row_version = :rowVersion, updated_by = :updatedBy, updated_at = :updatedAt
                WHERE id = :id AND merchant_code = :merchantCode
                    AND row_version = :expected AND deleted = FALSE
                """, params);
        if (changed != 1) {
            throw new CommonException(MerchantResultCodeEnum.CONFLICT);
        }
    }

    @Override
    public PageResult<Merchant> page(String code, String name, MerchantStatus status, int page, int size) {
        var sql = new StringBuilder(" FROM merchant_profile WHERE deleted = FALSE");
        var params = new MapSqlParameterSource();
        if (code != null && !code.isBlank()) {
            sql.append(" AND merchant_code = :code");
            params.addValue("code", code);
        }
        if (name != null && !name.isBlank()) {
            sql.append(" AND name LIKE :name");
            params.addValue("name", "%" + name + "%");
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.addValue("status", status.name());
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + sql, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
        var rows = jdbc.query(
                "SELECT " + COLUMNS + sql + " ORDER BY created_at DESC, id ASC LIMIT :limit OFFSET :offset",
                params,
                rowMapper);
        return new PageResult<>(
                rows.stream().map(converter::toMerchant).toList(), count == null ? 0 : count, page, size);
    }
}
