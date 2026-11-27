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
package io.github.loadup.productcenter.app.service;

import static io.github.loadup.productcenter.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.productcenter.app.model.limitcenter.command.LimitPolicyActivateCommand;
import io.github.loadup.productcenter.app.model.limitcenter.dto.LimitRuleDTO;
import io.github.loadup.productcenter.app.model.limitcenter.enums.LimitMetric;
import io.github.loadup.productcenter.app.model.limitcenter.enums.LimitWindowType;
import io.github.loadup.productcenter.app.model.limitcenter.enums.RefundAmountRestorePolicy;
import io.github.loadup.productcenter.app.model.limitcenter.enums.RefundCountRestorePolicy;
import io.github.loadup.productcenter.app.model.limitcenter.support.LimitPolicyHash;
import io.github.loadup.productcenter.domain.policy.LimitPolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Initial merchant policy: all eight stable counters exist, even while enforcement is disabled. */
@Component
public class LimitPolicyCompiler {
    public LimitPolicyActivateCommand compile(String payee, LimitPolicy policy) {
        try {
            DomainChecks.id(policy.policyId());
            if (policy.parentAggregationEnabled()) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            DomainChecks.id(payee);
            String scope = DigestUtils.sha256(
                    "merchant-acquiring-quota-v1:" + payee.getBytes(StandardCharsets.UTF_8).length + ":" + payee);
            List<LimitRuleDTO> rules = new ArrayList<>();
            for (var window : LimitPolicy.Window.values()) {
                for (var metric : LimitPolicy.Metric.values()) {
                    var rule = policy.rule(window, metric);
                    rules.add(new LimitRuleDTO(
                            scope,
                            payee,
                            LimitMetric.valueOf(metric.name()),
                            policy.currency(),
                            LimitWindowType.valueOf(window.name()),
                            policy.weekStart(),
                            rule.threshold() == null ? 0L : rule.threshold(),
                            rule.enabled(),
                            true));
                }
            }
            var unsigned = new LimitPolicyActivateCommand(
                    policy.policyId(),
                    1,
                    "0".repeat(64),
                    payee,
                    RefundAmountRestorePolicy.valueOf(policy.amountRestore().name()),
                    RefundCountRestorePolicy.valueOf(policy.countRestore().name()),
                    rules);
            var command = new LimitPolicyActivateCommand(
                    policy.policyId(),
                    1,
                    LimitPolicyHash.compute(unsigned),
                    payee,
                    unsigned.refundAmountRestore(),
                    unsigned.refundCountRestore(),
                    rules);
            return command;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new CommonException(UNSUPPORTED_POLICY);
        }
    }
}
