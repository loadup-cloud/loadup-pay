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
package io.github.loadup.pay.limitcenter.domain.gateway;

import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;

import java.util.Optional;

/**
 * Mutations and locks join the caller's database transaction.
 */
public interface LimitPolicyGateway {
    LimitPolicyHead lockOrCreateHead(String tenant, String policyId, String payeeMerchantId);

    Optional<LimitPolicyHead> lockHead(String tenant, String policyId);

    Optional<ActivatedLimitPolicy> findRevision(String tenant, String policyId, int revision);

    Optional<RuntimeLimitRule> lockRule(String tenant, LimitRule identity);

    void insertRule(String tenant, RuntimeLimitRule rule);

    void updateRule(String tenant, RuntimeLimitRule rule, long expectedVersion);

    void insertRevision(String tenant, ActivatedLimitPolicy policy);

    void updateHead(String tenant, LimitPolicyHead head, long expectedVersion);
}
