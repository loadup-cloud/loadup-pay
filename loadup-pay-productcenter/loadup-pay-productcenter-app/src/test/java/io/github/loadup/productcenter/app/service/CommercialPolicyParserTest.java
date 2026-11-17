/*
 * Copyright (C) 2026 LoadUp Cloud
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.client.error.ProductResultCodeEnum;
import io.github.loadup.productcenter.domain.model.TypedValue;
import io.github.loadup.productcenter.domain.model.ValueType;
import io.github.loadup.productcenter.domain.policy.CommercialPolicyParser;
import io.github.loadup.productcenter.domain.policy.LimitPolicy;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommercialPolicyParserTest {
    private final CommercialPolicyParser parser = new CommercialPolicyParser();

    @Test
    void retainsExactNumbersAndOriginalProvenance() {
        var values = AcquiringFixtures.values();
        values.put("limit.MONTH.AMOUNT.enabled", TypedValue.bool(true));
        values.put("limit.MONTH.AMOUNT.threshold", TypedValue.integer(9007199254740993L));
        values.put("payment.maxAmountMinor", TypedValue.integer(Long.MAX_VALUE));
        values.put("fee.rate", TypedValue.decimal("0.123456789012345678901234567890"));
        var source = AcquiringFixtures.configuration(values);
        var policy = parser.parse(source);
        assertSame(source, policy.source());
        assertEquals(source.origins(), policy.source().origins());
        assertEquals(Long.MAX_VALUE, policy.payment().maxAmountMinor());
        assertEquals(
                new BigDecimal("0.12345678901234567890123456789"), policy.fee().rate());
        assertEquals(
                9007199254740993L,
                policy.limits()
                        .rule(LimitPolicy.Window.MONTH, LimitPolicy.Metric.AMOUNT)
                        .threshold()
                        .longValue());
    }

    @Test
    void fullEntryRejectsMissingVocabularyAndUnknownKeys() {
        var values = AcquiringFixtures.values();
        values.remove("notify.timeoutMillis");
        unsupported(values);
        values = AcquiringFixtures.values();
        values.put("order.allowReopen", TypedValue.bool(true));
        unsupported(values);
        values = AcquiringFixtures.values();
        values.put("limit.YEAR.AMOUNT.enabled", TypedValue.bool(true));
        unsupported(values);
    }

    @Test
    void rejectsWrongTypesUnsupportedEnumsAndInexactIntegerConversions() {
        for (var replacement : Map.of(
                        "order.waitSeconds", TypedValue.text("600"),
                        "order.unpaidAction", TypedValue.text("UNKNOWN"),
                        "fee.rounding", TypedValue.text("UNNECESSARY"),
                        "notify.timeoutMillis", TypedValue.integer(2147483648L),
                        "limit.MONTH.AMOUNT.threshold", new TypedValue(ValueType.INTEGER, "9223372036854775808"))
                .entrySet()) {
            var values = AcquiringFixtures.values();
            values.put(replacement.getKey(), replacement.getValue());
            unsupported(values);
        }
    }

    @Test
    void preservesCrossFieldAndDisabledFieldConstraints() {
        for (var replacement : Map.ofEntries(
                        Map.entry("order.maxWaitSeconds", TypedValue.integer(599)),
                        Map.entry("order.extensionMaxSeconds", TypedValue.integer(1)),
                        Map.entry("payment.maxAmountMinor", TypedValue.integer(0)),
                        Map.entry("payment.maxSuccessfulPayments", TypedValue.integer(0)),
                        Map.entry("fee.rate", TypedValue.decimal("-0.1")),
                        Map.entry("fee.maximumMinor", TypedValue.integer(-1)),
                        Map.entry("refund.windowSeconds", TypedValue.integer(0)),
                        Map.entry("notify.retryMaximumSeconds", TypedValue.integer(0)),
                        Map.entry("notify.retryMultiplier", TypedValue.decimal("0.999")),
                        Map.entry("notify.targetRef", TypedValue.text("x".repeat(129))),
                        Map.entry("limit.parentPolicyRef", TypedValue.text("x".repeat(129))),
                        Map.entry("channel.routingProfileRef", TypedValue.text(" ")))
                .entrySet()) {
            var values = AcquiringFixtures.values();
            values.put(replacement.getKey(), replacement.getValue());
            unsupported(values);
        }
        var values = AcquiringFixtures.values();
        values.put("fee.maximumEnabled", TypedValue.bool(true));
        values.put("fee.minimumMinor", TypedValue.integer(2));
        values.put("fee.maximumMinor", TypedValue.integer(1));
        unsupported(values);
    }

    @Test
    void preservesConditionalReferenceAndMultiplierPrecisionRules() {
        var values = AcquiringFixtures.values();
        values.put("notify.targetRef", TypedValue.text("opaque\nreference"));
        values.put("notify.retryMultiplier", TypedValue.decimal("1.0000000000000000001"));
        parser.parse(AcquiringFixtures.configuration(values));
        values.put("notify.enabled", TypedValue.bool(true));
        values.put("notify.signingIdentityRef", TypedValue.text("signing-v1"));
        unsupported(values);
        values.put("notify.targetRef", TypedValue.text("target-v1"));
        unsupported(values);
        values.put("notify.retryMultiplier", TypedValue.decimal("2"));
        parser.parse(AcquiringFixtures.configuration(values));
    }

    private void unsupported(Map<String, TypedValue> values) {
        var failure = assertThrows(CommonException.class, () -> parser.parse(AcquiringFixtures.configuration(values)));
        assertSame(ProductResultCodeEnum.UNSUPPORTED_POLICY, failure.getResultCode());
    }
}
