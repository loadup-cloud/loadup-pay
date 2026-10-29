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
package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import io.github.loadup.pay.limitcenter.infrastructure.codec.LimitPolicyCodec;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyHeadDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyRevisionDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitRuleRuntimeDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class LimitPolicyGatewayImpl implements LimitPolicyGateway {
    private static final String HEAD = "id, tenant_id, created_at, updated_at, policy_id, payee_merchant_id, latest_version, row_version";
    private static final String REVISION = "id, tenant_id, created_at, updated_at, policy_id, policy_version, policy_hash, payee_merchant_id, compiled_rules, activated_at";
    private static final String RULE = "id, tenant_id, created_at, updated_at, owner_policy_id, policy_version, rule_scope_id, merchant_id, metric, currency, window_type, week_start, timezone, current_limit, enforcement_enabled, metering_enabled, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final LimitStorageConverter converter;
    private final LimitPolicyCodec codec;
    private final IdGenerator ids;
    private final Clock clock;

    public LimitPolicyGatewayImpl(NamedParameterJdbcTemplate jdbc, LimitStorageConverter converter,
                                  LimitPolicyCodec codec, IdGenerator ids, @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.codec = codec;
        this.ids = ids;
        this.clock = clock;
    }

    private static MapSqlParameterSource ruleKey(String tenant, LimitRule rule) {
        return new MapSqlParameterSource("tenant", tenant).addValue("scope", rule.ruleScopeId())
                .addValue("merchant", rule.merchantId()).addValue("metric", rule.metric().name())
                .addValue("currency", rule.currency()).addValue("window", rule.window().name());
    }

    @Override
    public Optional<LimitPolicyHead> lockHead(String tenant, String policy) {
        transaction(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("policy", policy);
        return jdbc.query("SELECT " + HEAD + " FROM limit_policy_head WHERE tenant_id = :tenant AND policy_id = :policy "
                        + "AND deleted = FALSE FOR UPDATE", args, BeanPropertyRowMapper.newInstance(LimitPolicyHeadDO.class))
                .stream().findFirst().map(converter::toHead);
    }

    @Override
    public LimitPolicyHead lockOrCreateHead(String tenant, String policy, String payee) {
        transaction(tenant);
        var row = converter.toHeadDO(tenant, new LimitPolicyHead(policy, payee, 0, 0));
        fill(row);
        jdbc.update("""
                INSERT INTO limit_policy_head (id, tenant_id, created_at, updated_at, deleted, policy_id, payee_merchant_id, latest_version, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :policyId, :payeeMerchantId, :latestVersion, :rowVersion)
                ON CONFLICT (tenant_id, policy_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        return lockHead(tenant, policy).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
    }

    @Override
    public Optional<ActivatedLimitPolicy> findRevision(String tenant, String policy, int revision) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("policy", policy).addValue("revision", revision);
        return jdbc.query("SELECT " + REVISION + " FROM limit_policy_revision WHERE tenant_id = :tenant "
                        + "AND policy_id = :policy AND policy_version = :revision AND deleted = FALSE", args,
                BeanPropertyRowMapper.newInstance(LimitPolicyRevisionDO.class)).stream().findFirst().map(row -> codec.decode(tenant, row));
    }

    @Override
    public Optional<RuntimeLimitRule> lockRule(String tenant, LimitRule rule) {
        transaction(tenant);
        var rows = jdbc.query("SELECT " + RULE + " FROM limit_rule_runtime WHERE tenant_id = :tenant AND rule_scope_id = :scope "
                + "AND merchant_id = :merchant AND metric = :metric AND currency = :currency AND window_type = :window "
                + "AND deleted = FALSE FOR UPDATE", ruleKey(tenant, rule), BeanPropertyRowMapper.newInstance(LimitRuleRuntimeDO.class));
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        if (!ZoneId.systemDefault().getId().equals(row.getTimezone())) throw new CommonException(SNAPSHOT_INVALID);
        return Optional.of(converter.toRuntime(row));
    }

    @Override
    public void insertRule(String tenant, RuntimeLimitRule rule) {
        transaction(tenant);
        var row = converter.toRuntimeDO(tenant, rule);
        fill(row);
        int count = jdbc.update("""
                INSERT INTO limit_rule_runtime (id, tenant_id, created_at, updated_at, deleted, owner_policy_id, policy_version,
                    rule_scope_id, merchant_id, metric, currency, window_type, week_start, timezone, current_limit,
                    enforcement_enabled, metering_enabled, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :ownerPolicyId, :policyVersion, :ruleScopeId,
                    :merchantId, :metric, :currency, :windowType, :weekStart, :timezone, :currentLimit,
                    :enforcementEnabled, :meteringEnabled, :rowVersion)
                ON CONFLICT (tenant_id, rule_scope_id, merchant_id, metric, currency, window_type) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new LimitWriteRaceException("Runtime rule business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateRule(String tenant, RuntimeLimitRule rule, long expectedVersion) {
        transaction(tenant);
        var row = converter.toRuntimeDO(tenant, rule);
        var args = ruleKey(tenant, rule.rule()).addValue("owner", row.getOwnerPolicyId()).addValue("policyVersion", row.getPolicyVersion())
                .addValue("weekStart", row.getWeekStart()).addValue("timezone", row.getTimezone()).addValue("limit", row.getCurrentLimit())
                .addValue("enforcement", row.getEnforcementEnabled()).addValue("metering", row.getMeteringEnabled())
                .addValue("version", row.getRowVersion()).addValue("expected", expectedVersion).addValue("updated", LocalDateTime.now(clock));
        int count = jdbc.update("""
                UPDATE limit_rule_runtime SET owner_policy_id = :owner, policy_version = :policyVersion,
                    week_start = :weekStart, timezone = :timezone, current_limit = :limit, enforcement_enabled = :enforcement,
                    metering_enabled = :metering, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND rule_scope_id = :scope AND merchant_id = :merchant AND metric = :metric
                    AND currency = :currency AND window_type = :window AND row_version = :expected AND deleted = FALSE
                """, args);
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insertRevision(String tenant, ActivatedLimitPolicy policy) {
        transaction(tenant);
        var row = converter.toRevisionDO(tenant, policy, codec.encode(policy));
        fill(row);
        int count = jdbc.update("""
                INSERT INTO limit_policy_revision (id, tenant_id, created_at, updated_at, deleted, policy_id,
                    policy_version, policy_hash, payee_merchant_id, compiled_rules, activated_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :policyId, :policyVersion, :policyHash,
                    :payeeMerchantId, :compiledRules, :activatedAt)
                ON CONFLICT (tenant_id, policy_id, policy_version) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new LimitWriteRaceException("Policy revision business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateHead(String tenant, LimitPolicyHead head, long expectedVersion) {
        transaction(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("policy", head.policyId())
                .addValue("payee", head.payeeMerchantId()).addValue("latest", head.latestVersion())
                .addValue("version", head.rowVersion()).addValue("expected", expectedVersion).addValue("updated", LocalDateTime.now(clock));
        int count = jdbc.update("""
                UPDATE limit_policy_head SET payee_merchant_id = :payee, latest_version = :latest,
                    row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND policy_id = :policy AND row_version = :expected AND deleted = FALSE
                """, args);
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void fill(io.github.loadup.commons.dataobject.BaseDO row) {
        LocalDateTime now = LocalDateTime.now(clock);
        row.setId(ids.generate());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void transaction(String tenant) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
