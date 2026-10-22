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

import io.github.loadup.modules.contract.domain.model.ConfigurationLayer;
import io.github.loadup.modules.contract.domain.model.ResolvedConfiguration;
import io.github.loadup.modules.contract.domain.model.TypedValue;
import io.github.loadup.modules.contract.domain.model.ValueOrigin;

import java.util.LinkedHashMap;
import java.util.Map;

final class AcquiringFixtures {
    static Map<String, TypedValue> values() {
        Map<String, TypedValue> v = new LinkedHashMap<>();
        Map.ofEntries(
                        Map.entry("trade.defaultCurrency", "CNY"),
                        Map.entry("trade.amountModel", "FIXED_TOTAL"),
                        Map.entry("trade.completionRule", "SUCCESS_SUM_EQUALS_TOTAL"),
                        Map.entry("order.unpaidAction", "KEEP_PENDING"),
                        Map.entry("fee.base", "ORDER_AMOUNT"),
                        Map.entry("fee.confirmationTrigger", "ORDER_FULLY_PAID"),
                        Map.entry("fee.rounding", "HALF_UP"),
                        Map.entry("fee.refundPolicy", "NO_RETURN"),
                        Map.entry("refund.executionMode", "SEQUENTIAL"),
                        Map.entry("refund.lateCollectionPolicy", "MANUAL_REVIEW"),
                        Map.entry("refund.allocationPolicy", "FIFO"),
                        Map.entry("limit.countingBasis", "INITIATED_AT"),
                        Map.entry("limit.timezone", "Asia/Shanghai"),
                        Map.entry("limit.policyRef", "policy"),
                        Map.entry("limit.refundAmountRestore", "NO_RESTORE"),
                        Map.entry("limit.refundCountRestore", "NO_RESTORE"),
                        Map.entry("channel.routingProfileRef", "routing-v1"),
                        Map.entry("checkout.returnTargetCode", "RETURN_V1"))
                .forEach((key, value) -> v.put(key, TypedValue.text(value)));
        Map.ofEntries(
                        Map.entry("order.waitSeconds", 600L),
                        Map.entry("order.maxWaitSeconds", 3600L),
                        Map.entry("order.extensionMaxSeconds", 0L),
                        Map.entry("checkout.sessionSeconds", 300L),
                        Map.entry("payment.minAmountMinor", 1L),
                        Map.entry("payment.maxAmountMinor", 100000L),
                        Map.entry("fee.fixedMinor", 0L),
                        Map.entry("fee.minimumMinor", 0L),
                        Map.entry("refund.windowSeconds", 86400L),
                        Map.entry("limit.weekStart", 1L),
                        Map.entry("notify.timeoutMillis", 1000L),
                        Map.entry("notify.retryInitialSeconds", 1L),
                        Map.entry("notify.retryMaximumSeconds", 60L),
                        Map.entry("notify.retryMaxAttempts", 5L),
                        Map.entry("notify.retryWindowSeconds", 3600L))
                .forEach((key, value) -> v.put(key, TypedValue.integer(value)));
        Map.of(
                        "order.extensionAllowed",
                        false,
                        "payment.maxSuccessfulPaymentsEnabled",
                        false,
                        "payment.allowExactRemainderBelowMin",
                        true,
                        "fee.maximumEnabled",
                        false,
                        "refund.enabled",
                        true,
                        "refund.partialAllowed",
                        true,
                        "limit.parentAggregationEnabled",
                        false,
                        "notify.enabled",
                        false,
                        "channel.WECHAT.DIRECT.H5.enabled",
                        true)
                .forEach((key, value) -> v.put(key, TypedValue.bool(value)));
        v.put("fee.rate", TypedValue.decimal("0.003"));
        v.put("notify.retryMultiplier", TypedValue.decimal("2"));
        return v;
    }

    static ResolvedConfiguration configuration(Map<String, TypedValue> values) {
        Map<String, ValueOrigin> origins = new LinkedHashMap<>();
        values.keySet().forEach(key -> origins.put(key, new ValueOrigin(ConfigurationLayer.PRODUCT, "acquiring", 1)));
        return new ResolvedConfiguration(values, origins);
    }
}
