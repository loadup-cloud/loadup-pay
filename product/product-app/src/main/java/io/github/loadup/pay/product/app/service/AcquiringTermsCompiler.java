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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.modules.contract.domain.model.ResolvedConfiguration;
import io.github.loadup.modules.contract.domain.model.TypedValue;
import io.github.loadup.modules.contract.domain.model.ValueType;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.client.dto.AcquiringTermsDTO;
import io.github.loadup.pay.product.client.dto.ChannelCapabilityDTO;
import io.github.loadup.pay.product.client.dto.FeeTermsDTO;
import io.github.loadup.pay.product.client.dto.LimitPolicyRefDTO;
import io.github.loadup.pay.product.client.dto.NotificationTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderTermsDTO;
import io.github.loadup.pay.product.client.dto.PaymentTermsDTO;
import io.github.loadup.pay.product.client.dto.RefundTermsDTO;
import io.github.loadup.pay.product.domain.model.FeeRefundPolicy;
import io.github.loadup.pay.product.domain.model.RefundAllocationPolicy;
import io.github.loadup.pay.product.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.product.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.product.domain.model.UnpaidOrderAction;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.UNSUPPORTED_POLICY;

/**
 * Version one accepts a closed scalar vocabulary and a trusted, matching policy receipt.
 */
@Component
public class AcquiringTermsCompiler {
    private final Validator validator;

    public AcquiringTermsCompiler(Validator validator) {
        this.validator = validator;
    }

    private static CommonException unsupported() {
        return new CommonException(UNSUPPORTED_POLICY);
    }

    public AcquiringTermsDTO compile(ResolvedConfiguration configuration, ActivatedLimitPolicy policy) {
        Map<String, TypedValue> values = configuration.values();
        try {
            Reader r = new Reader(values);
            r.exact("trade.defaultCurrency", "CNY");
            r.exact("trade.amountModel", "FIXED_TOTAL");
            r.exact("trade.completionRule", "SUCCESS_SUM_EQUALS_TOTAL");
            r.exact("fee.base", "ORDER_AMOUNT");
            r.exact("fee.confirmationTrigger", "ORDER_FULLY_PAID");
            r.exact("refund.executionMode", "SEQUENTIAL");
            r.exact("refund.lateCollectionPolicy", "MANUAL_REVIEW");
            r.exact("refund.allocationPolicy", "FIFO");
            r.exact("limit.countingBasis", "INITIATED_AT");
            long weekStart = r.integer("limit.weekStart");
            if (weekStart < 1 || weekStart > 7) throw unsupported();
            String policyId = r.text("limit.policyRef");
            if (!policyId.equals(policy.policyId())) throw unsupported();
            boolean parent = r.bool("limit.parentAggregationEnabled");
            String parentRef = r.optionalText("limit.parentPolicyRef");
            if (parent && (parentRef == null || parentRef.isBlank())) throw unsupported();
            for (String window : List.of("DAY", "WEEK", "MONTH", "LIFETIME")) {
                for (String metric : List.of("AMOUNT", "PAYMENT_COUNT")) {
                    String prefix = "limit." + window + "." + metric;
                    boolean enabled = r.optionalBool(prefix + ".enabled");
                    Long threshold = r.optionalInteger(prefix + ".threshold");
                    if (enabled && threshold == null || threshold != null && threshold < 0) throw unsupported();
                }
            }
            boolean countEnabled = r.bool("payment.maxSuccessfulPaymentsEnabled");
            Long count = r.optionalInteger("payment.maxSuccessfulPayments");
            if (countEnabled && count == null || count != null && count <= 0) throw unsupported();
            boolean maximumEnabled = r.bool("fee.maximumEnabled");
            Long maximum = r.optionalInteger("fee.maximumMinor");
            if (maximumEnabled && maximum == null || maximum != null && maximum < 0) throw unsupported();
            List<ChannelCapabilityDTO> capabilities = new ArrayList<>();
            for (Channel channel : Channel.values()) {
                for (AccessMode mode : AccessMode.values()) {
                    Set<PaymentMethod> methods = EnumSet.noneOf(PaymentMethod.class);
                    for (PaymentMethod method : PaymentMethod.values()) {
                        if (r.optionalBool(
                                "channel." + channel.name() + "." + mode.name() + "." + method.name() + ".enabled"))
                            methods.add(method);
                    }
                    if (!methods.isEmpty()) capabilities.add(new ChannelCapabilityDTO(channel, mode, methods));
                }
            }
            if ("UNNECESSARY".equals(r.text("fee.rounding"))) throw unsupported();
            var terms = new AcquiringTermsDTO(
                    1,
                    new OrderTermsDTO(
                            r.integer("order.waitSeconds"),
                            r.integer("order.maxWaitSeconds"),
                            UnpaidOrderAction.valueOf(r.text("order.unpaidAction")),
                            r.bool("order.extensionAllowed"),
                            r.integer("order.extensionMaxSeconds"),
                            r.integer("checkout.sessionSeconds")),
                    new PaymentTermsDTO(
                            r.integer("payment.minAmountMinor"),
                            r.integer("payment.maxAmountMinor"),
                            countEnabled ? Math.toIntExact(count) : null,
                            r.bool("payment.allowExactRemainderBelowMin")),
                    new FeeTermsDTO(
                            r.decimal("fee.rate"),
                            r.integer("fee.fixedMinor"),
                            RoundingMode.valueOf(r.text("fee.rounding")),
                            r.integer("fee.minimumMinor"),
                            maximumEnabled ? maximum : null,
                            FeeRefundPolicy.valueOf(r.text("fee.refundPolicy"))),
                    new RefundTermsDTO(
                            r.bool("refund.enabled"),
                            r.bool("refund.partialAllowed"),
                            r.integer("refund.windowSeconds"),
                            RefundAllocationPolicy.FIFO),
                    new NotificationTermsDTO(
                            r.bool("notify.enabled"),
                            r.optionalText("notify.targetRef"),
                            r.optionalText("notify.signingIdentityRef"),
                            Math.toIntExact(r.integer("notify.timeoutMillis")),
                            r.integer("notify.retryInitialSeconds"),
                            r.decimal("notify.retryMultiplier"),
                            r.integer("notify.retryMaximumSeconds"),
                            Math.toIntExact(r.integer("notify.retryMaxAttempts")),
                            r.integer("notify.retryWindowSeconds")),
                    new LimitPolicyRefDTO(
                            policyId,
                            policy.policyVersion(),
                            policy.policyHash(),
                            parent,
                            parentRef,
                            RefundAmountRestorePolicy.valueOf(r.text("limit.refundAmountRestore")),
                            RefundCountRestorePolicy.valueOf(r.text("limit.refundCountRestore"))),
                    capabilities,
                    r.text("channel.routingProfileRef"),
                    r.text("checkout.returnTargetCode"));
            r.requireAllConsumed();
            if (!validator.validate(terms).isEmpty()) throw unsupported();
            return terms;
        } catch (IllegalArgumentException | ArithmeticException failure) {
            throw unsupported();
        }
    }

    private static final class Reader {
        private final Map<String, TypedValue> values;
        private final Set<String> consumed = new HashSet<>();

        Reader(Map<String, TypedValue> values) {
            this.values = values;
        }

        private TypedValue get(String key, ValueType type, boolean optional) {
            consumed.add(key);
            TypedValue value = values.get(key);
            if (value == null && optional) return null;
            if (value == null || value.type() != type) throw unsupported();
            return value;
        }

        String text(String key) {
            return get(key, ValueType.STRING, false).value();
        }

        String optionalText(String key) {
            TypedValue value = get(key, ValueType.STRING, true);
            return value == null ? null : value.value();
        }

        long integer(String key) {
            return new BigInteger(get(key, ValueType.INTEGER, false).value()).longValueExact();
        }

        Long optionalInteger(String key) {
            TypedValue value = get(key, ValueType.INTEGER, true);
            return value == null ? null : new BigInteger(value.value()).longValueExact();
        }

        boolean bool(String key) {
            return Boolean.parseBoolean(get(key, ValueType.BOOLEAN, false).value());
        }

        boolean optionalBool(String key) {
            TypedValue value = get(key, ValueType.BOOLEAN, true);
            return value != null && Boolean.parseBoolean(value.value());
        }

        BigDecimal decimal(String key) {
            return new BigDecimal(get(key, ValueType.DECIMAL, false).value());
        }

        void exact(String key, String expected) {
            if (!expected.equals(text(key))) throw unsupported();
        }

        void requireAllConsumed() {
            if (!consumed.containsAll(values.keySet())) throw unsupported();
        }
    }
}
