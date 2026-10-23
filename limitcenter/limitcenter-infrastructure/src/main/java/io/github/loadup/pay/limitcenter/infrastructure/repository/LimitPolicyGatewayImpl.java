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

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitPolicyGateway;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import io.github.loadup.pay.limitcenter.infrastructure.codec.LimitPolicyCodec;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyHeadDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitPolicyRevisionDO;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitRuleRuntimeDO;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitPolicyHeadDOMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitPolicyRevisionDOMapper;
import io.github.loadup.pay.limitcenter.infrastructure.mapper.LimitRuleRuntimeDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class LimitPolicyGatewayImpl implements LimitPolicyGateway {
    private final LimitPolicyHeadDOMapper heads;
    private final LimitPolicyRevisionDOMapper revisions;
    private final LimitRuleRuntimeDOMapper rules;
    private final LimitStorageConverter converter;
    private final LimitPolicyCodec codec;

    public LimitPolicyGatewayImpl(
            LimitPolicyHeadDOMapper heads,
            LimitPolicyRevisionDOMapper revisions,
            LimitRuleRuntimeDOMapper rules,
            LimitStorageConverter converter,
            LimitPolicyCodec codec) {
        this.heads = heads;
        this.revisions = revisions;
        this.rules = rules;
        this.converter = converter;
        this.codec = codec;
    }

    private QueryWrapper headQuery(String tenant, String policy) {
        return QueryWrapper.create()
                .where(LimitPolicyHeadDO::getTenantId)
                .eq(tenant)
                .and(LimitPolicyHeadDO::getDeleted)
                .eq(0)
                .and(LimitPolicyHeadDO::getPolicyId)
                .eq(policy);
    }

    @Override
    public Optional<LimitPolicyHead> lockHead(String tenant, String policy) {
        return Optional.ofNullable(
                        heads.selectOneByQuery(headQuery(tenant, policy).forUpdate()))
                .map(converter::toHead);
    }

    @Override
    public LimitPolicyHead lockOrCreateHead(String tenant, String policy, String payee) {
        var existing = lockHead(tenant, policy);
        if (existing.isPresent()) return existing.get();
        var head = new LimitPolicyHead(policy, payee, 0, 0);
        heads.insert(converter.toHeadDO(tenant, head));
        return head;
    }

    @Override
    public Optional<ActivatedLimitPolicy> findRevision(String tenant, String policy, int revision) {
        var query = QueryWrapper.create()
                .where(LimitPolicyRevisionDO::getTenantId)
                .eq(tenant)
                .and(LimitPolicyRevisionDO::getDeleted)
                .eq(0)
                .and(LimitPolicyRevisionDO::getPolicyId)
                .eq(policy)
                .and(LimitPolicyRevisionDO::getPolicyVersion)
                .eq(revision);
        return Optional.ofNullable(revisions.selectOneByQuery(query)).map(row -> codec.decode(tenant, row));
    }

    private QueryWrapper ruleQuery(String tenant, LimitRule rule) {
        return QueryWrapper.create()
                .where(LimitRuleRuntimeDO::getTenantId)
                .eq(tenant)
                .and(LimitRuleRuntimeDO::getDeleted)
                .eq(0)
                .and(LimitRuleRuntimeDO::getRuleScopeId)
                .eq(rule.ruleScopeId())
                .and(LimitRuleRuntimeDO::getMerchantId)
                .eq(rule.merchantId())
                .and(LimitRuleRuntimeDO::getMetric)
                .eq(rule.metric().name())
                .and(LimitRuleRuntimeDO::getCurrency)
                .eq(rule.currency())
                .and(LimitRuleRuntimeDO::getWindowType)
                .eq(rule.window().name());
    }

    @Override
    public Optional<RuntimeLimitRule> lockRule(String tenant, LimitRule rule) {
        var row = rules.selectOneByQuery(ruleQuery(tenant, rule).forUpdate());
        if (row == null) return Optional.empty();
        if (!java.time.ZoneId.systemDefault().getId().equals(row.getTimezone())) throw new CommonException(SNAPSHOT_INVALID);
        return Optional.of(converter.toRuntime(row));
    }

    @Override
    public void insertRule(String tenant, RuntimeLimitRule rule) {
        rules.insert(converter.toRuntimeDO(tenant, rule));
    }

    @Override
    public void updateRule(String tenant, RuntimeLimitRule rule, long expectedVersion) {
        int changed = rules.updateByQuery(
                converter.toRuntimeDO(tenant, rule),
                true,
                ruleQuery(tenant, rule.rule())
                        .and(LimitRuleRuntimeDO::getRowVersion)
                        .eq(expectedVersion));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insertRevision(String tenant, ActivatedLimitPolicy policy) {
        revisions.insert(converter.toRevisionDO(tenant, policy, codec.encode(policy)));
    }

    @Override
    public void updateHead(String tenant, LimitPolicyHead head, long expectedVersion) {
        int changed = heads.updateByQuery(
                converter.toHeadDO(tenant, head),
                true,
                headQuery(tenant, head.policyId())
                        .and(LimitPolicyHeadDO::getRowVersion)
                        .eq(expectedVersion));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
