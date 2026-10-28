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
package io.github.loadup.pay.payment.infrastructure.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.product.client.enums.FeeRefundPolicy;
import io.github.loadup.pay.product.client.enums.RefundAllocationPolicy;
import io.github.loadup.pay.product.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.pay.product.client.enums.RefundCountRestorePolicy;
import io.github.loadup.pay.product.client.enums.UnpaidOrderAction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Owner-local storage format. No public Command, DTO, credentials or raw interaction objects are serialized here.
 */
public record PaymentIntentSnapshot(String format, String paymentId, String orderId, String allocationId,
                                    String admissionId,
                                    String payeeMerchantId, long amountMinor, String currency, String requestDigest,
                                    Channel channel, PaymentMethod method,
                                    @DiagnosticHidden String preparedInputRef,
                                    @DiagnosticHidden Admission admittedTerms, String subject, String payableUntil) {
    public static final String FORMAT = "pay.payment.intent.v1";

    public PaymentIntentSnapshot {
        require(FORMAT.equals(format), INVALID_ARGUMENT);
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(allocationId);
        DomainChecks.id(admissionId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.cnyAmount(amountMinor, currency);
        require(amountMinor > 0, INVALID_ARGUMENT);
        hash(requestDigest);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(method);
        Objects.requireNonNull(admittedTerms);
        if (preparedInputRef != null) DomainChecks.id(preparedInputRef);
        DomainChecks.text(subject, 256);
        DomainChecks.text(payableUntil, 64);
        OffsetDateTime.parse(payableUntil);
        require(admissionId.equals(admittedTerms.admissionId()) && orderId.equals(admittedTerms.orderId())
                && payeeMerchantId.equals(admittedTerms.payeeMerchantId()) && currency.equals(admittedTerms.currency())
                && amountMinor <= admittedTerms.orderAmountMinor(), INVALID_ARGUMENT);
    }

    private static void hash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
    }

    private static void optionalLength(String value, int maximum) {
        require(value == null || value.length() <= maximum, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public record Admission(String admissionId, String orderId, String payeeMerchantId, long orderAmountMinor,
                            String currency,
                            String contractId, int revision, long generation, String snapshotHash,
                            String compiledTermsHash, @DiagnosticHidden Terms terms) {
        public Admission {
            DomainChecks.id(admissionId);
            DomainChecks.id(orderId);
            DomainChecks.id(payeeMerchantId);
            DomainChecks.id(contractId);
            DomainChecks.cnyAmount(orderAmountMinor, currency);
            require(orderAmountMinor > 0 && revision > 0 && generation > 0, INVALID_ARGUMENT);
            DomainChecks.text(snapshotHash, 128);
            hash(compiledTermsHash);
            Objects.requireNonNull(terms);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record Terms(int schemaVersion, OrderPolicy order, PaymentPolicy payment, FeePolicy fee, RefundPolicy refund,
                        NotificationPolicy notification, LimitPolicy limits, List<Capability> capabilities,
                        String routingProfileRef, String returnTargetCode) {
        public Terms {
            require(schemaVersion == 1, INVALID_ARGUMENT);
            Objects.requireNonNull(order);
            Objects.requireNonNull(payment);
            Objects.requireNonNull(fee);
            Objects.requireNonNull(refund);
            Objects.requireNonNull(notification);
            Objects.requireNonNull(limits);
            capabilities = List.copyOf(capabilities);
            require(!capabilities.isEmpty(), INVALID_ARGUMENT);
            DomainChecks.text(routingProfileRef, 128);
            DomainChecks.text(returnTargetCode, 128);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record OrderPolicy(long waitSeconds, long maxWaitSeconds, UnpaidOrderAction unpaidAction,
                              boolean extensionAllowed,
                              long extensionMaxSeconds, long sessionSeconds) {
        public OrderPolicy {
            require(waitSeconds > 0 && maxWaitSeconds >= waitSeconds && sessionSeconds > 0 && extensionMaxSeconds >= 0
                    && (extensionAllowed ? extensionMaxSeconds > 0 : extensionMaxSeconds == 0) && unpaidAction != null, INVALID_ARGUMENT);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record PaymentPolicy(long minAmountMinor, long maxAmountMinor, Integer maxSuccessfulPayments,
                                boolean allowExactRemainderBelowMin) {
        public PaymentPolicy {
            require(minAmountMinor > 0 && maxAmountMinor >= minAmountMinor && (maxSuccessfulPayments == null || maxSuccessfulPayments > 0), INVALID_ARGUMENT);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record FeePolicy(BigDecimal rate, long fixedMinor, RoundingMode rounding, long minimumMinor,
                            Long maximumMinor, FeeRefundPolicy refundPolicy) {
        public FeePolicy {
            require(rate != null && rate.signum() >= 0 && fixedMinor >= 0 && minimumMinor >= 0
                    && (maximumMinor == null || maximumMinor >= minimumMinor) && rounding != null && refundPolicy != null, INVALID_ARGUMENT);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record RefundPolicy(boolean enabled, boolean partialAllowed, long windowSeconds,
                               RefundAllocationPolicy allocationPolicy) {
        public RefundPolicy {
            require(windowSeconds > 0 && allocationPolicy != null, INVALID_ARGUMENT);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record NotificationPolicy(boolean enabled, @DiagnosticHidden String targetRef,
                                     @DiagnosticHidden String signingIdentityRef,
                                     int timeoutMillis, long retryInitialSeconds, BigDecimal retryMultiplier,
                                     long retryMaximumSeconds, int retryMaxAttempts, long retryWindowSeconds) {
        public NotificationPolicy {
            optionalLength(targetRef, 128);
            optionalLength(signingIdentityRef, 128);
            require(timeoutMillis > 0 && retryInitialSeconds > 0 && retryMaximumSeconds >= retryInitialSeconds && retryMaxAttempts > 0
                    && retryWindowSeconds > 0 && retryMultiplier != null && retryMultiplier.compareTo(BigDecimal.ONE) >= 0, INVALID_ARGUMENT);
            if (enabled) {
                DomainChecks.text(targetRef, 128);
                DomainChecks.text(signingIdentityRef, 128);
                var normalized = retryMultiplier.stripTrailingZeros();
                require((long) normalized.precision() - normalized.scale() <= 20 && normalized.scale() <= 18, INVALID_ARGUMENT);
            }
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record LimitPolicy(String policyId, int policyVersion, String policyHash, boolean parentAggregationEnabled,
                              String parentPolicyRef, RefundAmountRestorePolicy amountRestore,
                              RefundCountRestorePolicy countRestore) {
        public LimitPolicy {
            DomainChecks.id(policyId);
            hash(policyHash);
            require(policyVersion > 0 && amountRestore != null && countRestore != null, INVALID_ARGUMENT);
            optionalLength(parentPolicyRef, 128);
            if (parentAggregationEnabled) DomainChecks.text(parentPolicyRef, 128);
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }

    public record Capability(Channel channel, AccessMode accessMode, Set<PaymentMethod> methods) {
        public Capability {
            Objects.requireNonNull(channel);
            Objects.requireNonNull(accessMode);
            Objects.requireNonNull(methods);
            require(!methods.isEmpty(), INVALID_ARGUMENT);
            methods = Collections.unmodifiableSet(EnumSet.copyOf(methods));
        }

        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
