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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.modules.contract.domain.model.ResolvedConfiguration;
import io.github.loadup.modules.contract.domain.model.TypedValue;
import io.github.loadup.modules.contract.domain.model.ValueType;
import io.github.loadup.pay.limitcenter.client.command.LimitPolicyActivateCommand;
import io.github.loadup.pay.limitcenter.client.dto.LimitRuleDTO;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

/**
 * Initial merchant policy: all eight stable counters exist, even while enforcement is disabled.
 */
@Component
public class LimitPolicyCompiler {
    private final AcquiringTermsCompiler terms;

    public LimitPolicyCompiler(AcquiringTermsCompiler terms) {
        this.terms = terms;
    }

    private static String text(Map<String, TypedValue> values, String key) {
        var value = values.get(key);
        if (value == null || value.type() != ValueType.STRING) {
            throw new CommonException(UNSUPPORTED_POLICY);
        }
        return value.value();
    }

    private static boolean bool(Map<String, TypedValue> values, String key, boolean fallback) {
        var value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.type() != ValueType.BOOLEAN) {
            throw new CommonException(UNSUPPORTED_POLICY);
        }
        return Boolean.parseBoolean(value.value());
    }

    private static Long integer(Map<String, TypedValue> values, String key, Long fallback) {
        var value = values.get(key);
        if (value == null) {
            if (fallback == null) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            return fallback;
        }
        if (value.type() != ValueType.INTEGER) {
            throw new CommonException(UNSUPPORTED_POLICY);
        }
        return new BigInteger(value.value()).longValueExact();
    }

    public LimitPolicyActivateCommand compile(String tenant, String payee, ResolvedConfiguration configuration) {
        try {
            var values = configuration.values();
            String policy = text(values, "limit.policyRef");
            DomainChecks.id(policy);
            if (!"CNY".equals(text(values, "trade.defaultCurrency"))
                    || !"INITIATED_AT".equals(text(values, "limit.countingBasis"))
                    || bool(values, "limit.parentAggregationEnabled", false)) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            int weekStart = Math.toIntExact(integer(values, "limit.weekStart", null));
            if (weekStart < 1 || weekStart > 7) {
                throw new CommonException(UNSUPPORTED_POLICY);
            }
            DomainChecks.id(tenant);
            DomainChecks.id(payee);
            String scope = DigestUtils.sha256("merchant-acquiring-quota-v1:"
                    + tenant.getBytes(StandardCharsets.UTF_8).length + ":" + tenant
                    + payee.getBytes(StandardCharsets.UTF_8).length + ":" + payee);
            List<LimitRuleDTO> rules = new ArrayList<>();
            for (var window : LimitWindowType.values()) {
                for (var metric : LimitMetric.values()) {
                    String prefix = "limit." + window.name() + "." + metric.name();
                    boolean enabled = bool(values, prefix + ".enabled", false);
                    Long threshold = integer(values, prefix + ".threshold", enabled ? null : 0L);
                    if (threshold < 0) {
                        throw new CommonException(UNSUPPORTED_POLICY);
                    }
                    rules.add(
                            new LimitRuleDTO(scope, payee, metric, "CNY", window, weekStart, threshold, enabled, true));
                }
            }
            var unsigned = new LimitPolicyActivateCommand(
                    policy,
                    1,
                    "0".repeat(64),
                    payee,
                    RefundAmountRestorePolicy.valueOf(text(values, "limit.refundAmountRestore")),
                    RefundCountRestorePolicy.valueOf(text(values, "limit.refundCountRestore")),
                    rules);
            var command = new LimitPolicyActivateCommand(
                    policy,
                    1,
                    LimitPolicyHash.compute(tenant, unsigned),
                    payee,
                    unsigned.refundAmountRestore(),
                    unsigned.refundCountRestore(),
                    rules);
            // This INIT binding validates the complete closed vocabulary; it is never treated as an activation ACK.
            terms.compile(configuration, new ActivatedLimitPolicy(policy, 1, command.policyHash(), "INIT", null));
            return command;
        } catch (IllegalArgumentException | ArithmeticException invalid) {
            throw new CommonException(UNSUPPORTED_POLICY);
        }
    }
}
