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
package io.github.loadup.productcenter.infrastructure.repository;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.domain.gateway.CatalogGateway;
import io.github.loadup.productcenter.domain.model.CatalogEntry;
import io.github.loadup.productcenter.domain.model.CatalogKind;
import io.github.loadup.productcenter.domain.model.CatalogStatus;
import io.github.loadup.productcenter.infrastructure.dataobject.CatalogVersionRow;
import io.github.loadup.productcenter.infrastructure.mapper.CatalogVersionRowMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogGatewayImpl extends ServiceImpl<CatalogVersionRowMapper, CatalogVersionRow>
        implements CatalogGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CatalogGatewayImpl(NamedParameterJdbcTemplate jdbc, CatalogVersionRowMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_LOOKUP_ID_REQUIRED);
        }
        return value;
    }

    private static MapSqlParameterSource params(CatalogEntry entry) {
        return new MapSqlParameterSource("id", required(entry.id()))
                .addValue("kind", entry.kind().name())
                .addValue("code", entry.code())
                .addValue("version", entry.version())
                .addValue("status", entry.status().name())
                .addValue("rowVersion", entry.rowVersion())
                .addValue("content", entry.content())
                .addValue("updatedBy", entry.updatedBy());
    }

    private static CatalogEntry toEntry(CatalogVersionRow row) {
        return new CatalogEntry(
                row.getId(),
                CatalogKind.valueOf(row.getKind()),
                row.getCode(),
                row.getVersion(),
                CatalogStatus.valueOf(row.getStatus()),
                row.getRowVersion(),
                row.getContent(),
                row.getUpdatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static CatalogEntry map(ResultSet rs, int row) throws SQLException {
        return new CatalogEntry(
                rs.getString("id"),
                CatalogKind.valueOf(rs.getString("kind")),
                rs.getString("code"),
                rs.getInt("version"),
                CatalogStatus.valueOf(rs.getString("status")),
                rs.getLong("row_version"),
                rs.getString("content"),
                rs.getString("updated_by"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    @Override
    public Optional<CatalogEntry> find(String id) {
        return getByIdOpt(required(id)).map(CatalogGatewayImpl::toEntry);
    }

    @Override
    public Optional<CatalogEntry> lock(String id) {
        var args = new MapSqlParameterSource().addValue("id", required(id));
        return jdbc
                .query(
                        "SELECT * FROM contract_catalog_version WHERE id = :id " + "AND deleted = FALSE FOR UPDATE",
                        args,
                        CatalogGatewayImpl::map)
                .stream()
                .findFirst();
    }

    @Override
    public void insert(CatalogEntry entry) {
        LocalDateTime now = LocalDateTime.now(clock);
        var args = params(entry)
                .addValue("createdAt", entry.createdAt() == null ? now : entry.createdAt())
                .addValue("updatedAt", now);
        int count = jdbc.update("""
                INSERT INTO contract_catalog_version (id, kind, code, version, status, row_version,
                    content, updated_by, created_at, updated_at, deleted)
                VALUES (:id, :kind, :code, :version, :status, :rowVersion, :content, :updatedBy,
                    :createdAt, :updatedAt, FALSE)
                ON CONFLICT (kind, code, version) DO NOTHING
                """, args);
        if (count == 0) {
            throw new CommonException(ContractResultCodeEnum.CONFLICT);
        }
        if (count != 1) {
            throw new IllegalStateException("Unexpected catalog insert count");
        }
    }

    @Override
    public void update(CatalogEntry entry, long expected) {
        var args = params(entry).addValue("expected", expected).addValue("updatedAt", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE contract_catalog_version SET kind = :kind, code = :code, version = :version, status = :status,
                    row_version = :rowVersion, content = :content, updated_by = :updatedBy, updated_at = :updatedAt
                WHERE id = :id AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(ContractResultCodeEnum.CATALOG_VERSION_CHANGED);
        }
    }

    @Override
    public PageResult<CatalogEntry> page(CatalogKind kind, CatalogStatus status, String code, int page, int size) {
        var sql = new StringBuilder(" FROM contract_catalog_version WHERE kind = :kind AND deleted = FALSE");
        var args = new MapSqlParameterSource().addValue("kind", kind.name());
        if (status != null) {
            sql.append(" AND status = :status");
            args.addValue("status", status.name());
        }
        if (code != null && !code.isBlank()) {
            sql.append(" AND code = :code");
            args.addValue("code", code);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + sql, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
        var rows = jdbc.query(
                "SELECT *" + sql + " ORDER BY updated_at DESC, id ASC LIMIT :limit OFFSET :offset",
                args,
                CatalogGatewayImpl::map);
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }
}
