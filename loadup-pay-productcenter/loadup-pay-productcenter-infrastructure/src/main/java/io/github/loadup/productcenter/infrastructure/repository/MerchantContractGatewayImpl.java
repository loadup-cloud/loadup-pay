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

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.domain.gateway.MerchantContractGateway;
import io.github.loadup.productcenter.domain.gateway.RequestKeyConflictException;
import io.github.loadup.productcenter.domain.model.ContractRecord;
import io.github.loadup.productcenter.domain.model.ContractRevisionRecord;
import io.github.loadup.productcenter.domain.model.ContractStatus;
import io.github.loadup.productcenter.infrastructure.dataobject.ContractRevisionRow;
import io.github.loadup.productcenter.infrastructure.dataobject.MerchantContractRow;
import io.github.loadup.productcenter.infrastructure.mapper.ContractRevisionRowMapper;
import io.github.loadup.productcenter.infrastructure.mapper.MerchantContractRowMapper;
import io.github.loadup.productcenter.infrastructure.table.Tables;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MerchantContractGatewayImpl extends ServiceImpl<MerchantContractRowMapper, MerchantContractRow>
        implements MerchantContractGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final ContractRevisionRowMapper revisionMapper;
    private final Clock clock;

    public MerchantContractGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            MerchantContractRowMapper mapper,
            ContractRevisionRowMapper revisionMapper,
            Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.revisionMapper = revisionMapper;
        this.clock = clock;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_LOOKUP_ID_REQUIRED);
        }
        return value;
    }

    private static boolean scopeConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException sql
                    && "23505".equals(sql.getSQLState())
                    && sql.getServerErrorMessage() != null
                    && "uk_merchant_contract_scope"
                            .equals(sql.getServerErrorMessage().getConstraint())) {
                return true;
            }
        }
        return false;
    }

    private static ContractRecord toContract(MerchantContractRow row) {
        return new ContractRecord(
                row.getId(),
                row.getMerchantId(),
                row.getScopeKey(),
                row.getPlanVersionId(),
                ContractStatus.valueOf(row.getStatus()),
                row.getGeneration(),
                row.getRequestKey(),
                row.getRequestDigest(),
                row.getCreatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static ContractRevisionRecord toRevision(ContractRevisionRow row) {
        return new ContractRevisionRecord(
                row.getId(),
                row.getContractId(),
                row.getRevision(),
                row.getSnapshot(),
                row.getSnapshotHash(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private static ContractRecord contract(ResultSet rs, int row) throws SQLException {
        return new ContractRecord(
                rs.getString("id"),
                rs.getString("merchant_id"),
                rs.getString("scope_key"),
                rs.getString("plan_version_id"),
                ContractStatus.valueOf(rs.getString("status")),
                rs.getLong("generation"),
                rs.getString("request_key"),
                rs.getString("request_digest"),
                rs.getString("created_by"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    private Optional<ContractRecord> find(String condition, MapSqlParameterSource args) {
        return jdbc
                .query(
                        "SELECT * FROM merchant_contract WHERE deleted = FALSE AND " + condition,
                        args,
                        MerchantContractGatewayImpl::contract)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ContractRecord> find(String id) {
        return getByIdOpt(required(id)).map(MerchantContractGatewayImpl::toContract);
    }

    @Override
    public Optional<ContractRecord> findByScope(String merchant, String key) {
        var query = QueryWrapper.create()
                .where(Tables.MERCHANT_CONTRACT_ROW.MERCHANT_ID.eq(required(merchant)))
                .and(Tables.MERCHANT_CONTRACT_ROW.SCOPE_KEY.eq(required(key)));
        return getOneOpt(query).map(MerchantContractGatewayImpl::toContract);
    }

    @Override
    public Optional<ContractRecord> lockByScope(String merchant, String key) {
        return find(
                "merchant_id = :merchant AND scope_key = :key FOR UPDATE",
                new MapSqlParameterSource()
                        .addValue("merchant", required(merchant))
                        .addValue("key", required(key)));
    }

    @Override
    public Optional<ContractRecord> findByRequest(String key) {
        var query = QueryWrapper.create().where(Tables.MERCHANT_CONTRACT_ROW.REQUEST_KEY.eq(required(key)));
        return getOneOpt(query).map(MerchantContractGatewayImpl::toContract);
    }

    @Override
    public Optional<ContractRevisionRecord> revision(String id, int version) {
        var query = QueryWrapper.create()
                .where(Tables.CONTRACT_REVISION_ROW.CONTRACT_ID.eq(required(id)))
                .and(Tables.CONTRACT_REVISION_ROW.REVISION.eq(version));
        return Optional.ofNullable(revisionMapper.selectOneByQuery(query)).map(MerchantContractGatewayImpl::toRevision);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(ContractRecord record, ContractRevisionRecord revision) {
        if (!required(record.id()).equals(required(revision.contractId()))) {
            throw new IllegalArgumentException("Contract revision must belong to the new contract");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        var args = new MapSqlParameterSource("id", record.id())
                .addValue("merchant", record.merchantId())
                .addValue("scope", record.scopeKey())
                .addValue("plan", record.planVersionId())
                .addValue("status", record.status().name())
                .addValue("generation", record.generation())
                .addValue("key", record.requestKey())
                .addValue("digest", record.requestDigest())
                .addValue("actor", record.createdBy())
                .addValue("created", record.createdAt() == null ? now : record.createdAt())
                .addValue("updated", now);
        int inserted;
        try {
            inserted = jdbc.update("""
                    INSERT INTO merchant_contract (id, merchant_id, scope_key, plan_version_id, status, generation,
                        request_key, request_digest, created_by, created_at, updated_at, deleted)
                    VALUES (:id, :merchant, :scope, :plan, :status, :generation, :key, :digest, :actor, :created, :updated, FALSE)
                    ON CONFLICT (request_key) DO NOTHING
                    """, args);
        } catch (DuplicateKeyException failure) {
            if (scopeConflict(failure)) {
                throw new CommonException(ContractResultCodeEnum.CONFLICT, failure);
            }
            throw failure;
        }
        if (inserted == 0) {
            throw new RequestKeyConflictException();
        }
        if (inserted != 1) {
            throw new IllegalStateException("Unexpected contract insert count");
        }
        var rev = new MapSqlParameterSource("id", required(revision.id()))
                .addValue("contract", revision.contractId())
                .addValue("revision", revision.revision())
                .addValue("snapshot", revision.snapshot())
                .addValue("hash", revision.snapshotHash())
                .addValue("created", revision.createdAt() == null ? now : revision.createdAt())
                .addValue("updated", now);
        if (jdbc.update("""
                INSERT INTO merchant_contract_revision (id, contract_id, revision, snapshot, snapshot_hash,
                    created_at, updated_at, deleted) VALUES (:id, :contract, :revision, :snapshot, :hash, :created, :updated, FALSE)
                """, rev) != 1) {
            throw new IllegalStateException("Unexpected contract revision insert count");
        }
    }

    @Override
    public void updateStatus(String id, long generation, ContractStatus status) {
        var args = new MapSqlParameterSource("id", required(id))
                .addValue("generation", generation)
                .addValue("next", Math.addExact(generation, 1))
                .addValue("status", status.name())
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE merchant_contract SET status = :status, generation = :next, updated_at = :updated
                WHERE id = :id AND generation = :generation AND status <> 'TERMINATED' AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(ContractResultCodeEnum.CONTRACT_STATE_CHANGED_OR_TERMINATED);
        }
    }

    @Override
    public PageResult<ContractRecord> page(String merchant, int page, int size) {
        var sql = new StringBuilder(" FROM merchant_contract WHERE deleted = FALSE");
        var args = new MapSqlParameterSource();
        if (merchant != null && !merchant.isBlank()) {
            sql.append(" AND merchant_id = :merchant");
            args.addValue("merchant", merchant);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + sql, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) (page - 1) * size);
        var rows = jdbc.query(
                "SELECT *" + sql + " ORDER BY created_at DESC, id ASC LIMIT :limit OFFSET :offset",
                args,
                MerchantContractGatewayImpl::contract);
        return new PageResult<>(rows, total == null ? 0 : total, page, size);
    }
}
