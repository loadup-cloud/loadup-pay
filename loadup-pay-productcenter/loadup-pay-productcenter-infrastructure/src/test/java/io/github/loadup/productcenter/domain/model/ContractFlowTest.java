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
package io.github.loadup.productcenter.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContractFlowTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    private static final String ITEM = "basic.wechat";

    private ParameterDefinition fee() {
        return new ParameterDefinition(
                "fee.rate",
                ValueType.DECIMAL,
                true,
                TypedValue.decimal("0.006"),
                new BigDecimal("0.003"),
                new BigDecimal("0.01"),
                Set.of(),
                Set.of(ConfigurationLayer.BUNDLE, ConfigurationLayer.SALES_PLAN, ConfigurationLayer.MERCHANT));
    }

    private ProductVersion product() {
        return new ProductVersion(
                "WECHAT", 1, "payment.collect", Map.of("fee.rate", fee()), Condition.always(), Set.of(), Set.of());
    }

    private SalesPlanVersion plan(
            ProductVersion product,
            Map<String, TypedValue> bundleValues,
            Map<String, TypedValue> planValues,
            Map<String, OverridePolicy> policies) {
        var bundle = new BundleVersion(
                "BASE",
                1,
                List.of(new BundleVersion.Item("wechat", product, true, true, bundleValues, Condition.always())));
        return new SalesPlanCompiler()
                .publish(new SalesPlanDraft(
                        "STANDARD",
                        1,
                        List.of(new SalesPlanDraft.BundleSelection("basic", bundle)),
                        Map.of(ITEM, planValues),
                        Map.of(ITEM, policies),
                        Condition.always(),
                        Condition.always(),
                        START,
                        END));
    }

    private OverridePolicy policy() {
        return new OverridePolicy(new BigDecimal("0.0038"), new BigDecimal("0.006"), Set.of());
    }

    private MerchantContractRevision sign(
            SalesPlanVersion plan, Map<String, TypedValue> custom, Instant from, Instant to) {
        return new ContractSigner()
                .sign(
                        "M1",
                        "STORE1",
                        "C1",
                        1,
                        plan,
                        plan.defaultSelection(),
                        Map.of(ITEM, custom),
                        Map.of(),
                        START,
                        from,
                        to);
    }

    private MerchantContract contract(MerchantContractRevision revision, ContractStatus status) {
        return new MerchantContract("M1", "STORE1", "C1", status, 1, List.of(revision));
    }

    @Test
    void resolvesEveryLayerAndRetainsFieldOrigin() {
        var plan = plan(
                product(),
                Map.of("fee.rate", TypedValue.decimal("0.0055")),
                Map.of("fee.rate", TypedValue.decimal("0.005")),
                Map.of("fee.rate", policy()));
        var revision = sign(plan, Map.of("fee.rate", TypedValue.decimal("0.0045")), START, END);
        var configuration = revision.items().get(ITEM).configuration();
        assertThat(configuration.values().get("fee.rate")).isEqualTo(TypedValue.decimal("0.0045"));
        assertThat(configuration.origins().get("fee.rate"))
                .isEqualTo(new ValueOrigin(ConfigurationLayer.MERCHANT, "C1", 1));
        assertThat(plan.items().get(ITEM).configuration().values().get("fee.rate"))
                .isEqualTo(TypedValue.decimal("0.005"));
    }

    @Test
    void rejectsOutOfRangeAndUnauthorizedMerchantValues() {
        var plan = plan(product(), Map.of(), Map.of(), Map.of("fee.rate", policy()));
        assertThatThrownBy(() -> sign(plan, Map.of("fee.rate", TypedValue.decimal("0.003")), START, END))
                .isInstanceOf(CommonException.class);
        var locked = plan(product(), Map.of(), Map.of(), Map.of());
        assertThatThrownBy(() -> sign(locked, Map.of("fee.rate", TypedValue.decimal("0.0045")), START, END))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("permission");
        assertThatThrownBy(() -> sign(plan, Map.of("unknown", TypedValue.text("x")), START, END))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("Unknown parameter");
    }

    @Test
    void rejectsWidenedOrEmptyNegotiationRanges() {
        assertThatThrownBy(() -> plan(
                        product(),
                        Map.of(),
                        Map.of(),
                        Map.of("fee.rate", new OverridePolicy(new BigDecimal("0.002"), null, Set.of()))))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("widens");
        assertThatThrownBy(() -> plan(
                        product(),
                        Map.of(),
                        Map.of(),
                        Map.of("fee.rate", new OverridePolicy(new BigDecimal("0.02"), null, Set.of()))))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("Inverted");
    }

    @Test
    void rejectsUneditableBundleAndPlanOverrides() {
        var locked = new ParameterDefinition(
                "fee.rate", ValueType.DECIMAL, true, TypedValue.decimal("0.006"), null, null, Set.of(), Set.of());
        var product = new ProductVersion(
                "WECHAT", 1, "payment.collect", Map.of("fee.rate", locked), Condition.always(), Set.of(), Set.of());
        assertThatThrownBy(() -> plan(product, Map.of("fee.rate", TypedValue.decimal("0.005")), Map.of(), Map.of()))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("forbidden");
        assertThatThrownBy(() -> plan(product, Map.of(), Map.of("fee.rate", TypedValue.decimal("0.005")), Map.of()))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("forbidden");
    }

    @Test
    void freezesDefaultsInsteadOfConsultingLaterProducts() {
        var old = sign(plan(product(), Map.of(), Map.of(), Map.of()), Map.of(), START, END);
        var changedFee = new ParameterDefinition(
                "fee.rate", ValueType.DECIMAL, true, TypedValue.decimal("0.009"), null, null, Set.of(), Set.of());
        var changed = new ProductVersion(
                "WECHAT", 2, "payment.collect", Map.of("fee.rate", changedFee), Condition.always(), Set.of(), Set.of());
        var newer = sign(plan(changed, Map.of(), Map.of(), Map.of()), Map.of(), START, END);
        assertThat(old.items().get(ITEM).configuration().values().get("fee.rate"))
                .isEqualTo(TypedValue.decimal("0.006"));
        assertThat(newer.snapshotHash()).isNotEqualTo(old.snapshotHash());
    }

    @Test
    void immutableInputCopiesDoNotFollowCallerMutation() {
        var parameters = new LinkedHashMap<String, ParameterDefinition>();
        parameters.put("fee.rate", fee());
        var product =
                new ProductVersion("WECHAT", 1, "payment.collect", parameters, Condition.always(), Set.of(), Set.of());
        parameters.clear();
        assertThat(product.parameters()).hasSize(1);
        assertThatThrownBy(() -> product.parameters().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hashIsIndependentOfMapOrderAndDecimalSpellingButSensitiveToIdentity() {
        var revision = sign(
                plan(product(), Map.of(), Map.of(), Map.of("fee.rate", policy())),
                Map.of("fee.rate", TypedValue.decimal("0.004500")),
                START,
                END);
        var same = sign(
                plan(product(), Map.of(), Map.of(), Map.of("fee.rate", policy())),
                Map.of("fee.rate", TypedValue.decimal("0.0045")),
                START,
                END);
        assertThat(revision.snapshotHash()).isEqualTo(same.snapshotHash()).startsWith("contract-terms-v1:");
        var otherMerchant = new MerchantContractRevision(
                "M2",
                "STORE1",
                "C1",
                1,
                revision.planCode(),
                revision.planVersion(),
                START,
                START,
                END,
                revision.items());
        assertThat(otherMerchant.snapshotHash()).isNotEqualTo(revision.snapshotHash());
    }

    @Test
    void requiredFieldsMayWaitForMerchantInputButCannotRemainUnfilled() {
        var required = new ParameterDefinition(
                "account", ValueType.STRING, true, null, null, null, Set.of(), Set.of(ConfigurationLayer.MERCHANT));
        var product = new ProductVersion(
                "WECHAT", 1, "payment.collect", Map.of("account", required), Condition.always(), Set.of(), Set.of());
        assertThatThrownBy(() -> plan(product, Map.of(), Map.of(), Map.of()))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("cannot be filled");
        var plan = plan(product, Map.of(), Map.of(), Map.of("account", new OverridePolicy(null, null, Set.of())));
        assertThatThrownBy(() -> sign(plan, Map.of(), START, END))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("Missing required");
        assertThat(sign(plan, Map.of("account", TypedValue.text("trusted-account-ref")), START, END)
                        .items())
                .hasSize(1);
    }

    @Test
    void productSelectionEnforcesRequiredDependenciesAndExclusions() {
        var required = plan(product(), Map.of(), Map.of(), Map.of());
        assertThatThrownBy(() -> new ContractSigner()
                        .sign("M1", "STORE1", "C1", 1, required, Set.of(), Map.of(), Map.of(), START, START, END))
                .isInstanceOf(CommonException.class);
        var dependent = new ProductVersion(
                "WECHAT", 1, "payment.collect", Map.of(), Condition.always(), Set.of("ALIPAY"), Set.of());
        assertThatThrownBy(() -> plan(dependent, Map.of(), Map.of(), Map.of()))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("dependency");
        var excluded = new ProductVersion(
                "ALIPAY", 1, "payment.collect", Map.of(), Condition.always(), Set.of(), Set.of("WECHAT"));
        var bundle = new BundleVersion(
                "BASE",
                1,
                List.of(
                        new BundleVersion.Item("wechat", product(), true, true, Map.of(), Condition.always()),
                        new BundleVersion.Item("alipay", excluded, false, false, Map.of(), Condition.always())));
        var plan = new SalesPlanCompiler()
                .publish(new SalesPlanDraft(
                        "STANDARD",
                        1,
                        List.of(new SalesPlanDraft.BundleSelection("basic", bundle)),
                        Map.of(),
                        Map.of(),
                        Condition.always(),
                        Condition.always(),
                        START,
                        END));
        assertThatThrownBy(() -> new ContractSigner()
                        .sign(
                                "M1",
                                "STORE1",
                                "C1",
                                1,
                                plan,
                                Set.of(ITEM, "basic.alipay"),
                                Map.of(),
                                Map.of(),
                                START,
                                START,
                                END))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("exclusive");
    }

    @Test
    void rejectsMissingEligibilityAndClosedSaleWindow() {
        var bundle = new BundleVersion(
                "BASE",
                1,
                List.of(new BundleVersion.Item("wechat", product(), true, true, Map.of(), Condition.always())));
        var eligible = new Condition.Compare(
                "merchant.industry", ComparisonOperator.EQ, List.of(TypedValue.text("RETAIL")), null);
        var plan = new SalesPlanCompiler()
                .publish(new SalesPlanDraft(
                        "STANDARD",
                        1,
                        List.of(new SalesPlanDraft.BundleSelection("basic", bundle)),
                        Map.of(),
                        Map.of(),
                        eligible,
                        Condition.always(),
                        START,
                        END));
        assertThatThrownBy(() -> sign(plan, Map.of(), START, END))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("eligibility");
        assertThatThrownBy(() -> new ContractSigner()
                        .sign(
                                "M1",
                                "STORE1",
                                "C1",
                                1,
                                plan,
                                plan.defaultSelection(),
                                Map.of(),
                                Map.of("merchant.industry", TypedValue.text("RETAIL")),
                                END,
                                END,
                                null))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("sale window");
    }

    @Test
    void runtimeRespectsHalfOpenIntervalsAndNeverReturnsConfigurationOnDenial() {
        var revision = sign(plan(product(), Map.of(), Map.of(), Map.of()), Map.of(), START, END);
        var resolver = new ContractRuntimeResolver();
        var contract = contract(revision, ContractStatus.NORMAL);
        assertThat(resolver.resolve(contract, ITEM, START, Map.of()).allowed()).isTrue();
        assertThat(resolver.resolve(contract, ITEM, START.minusSeconds(1), Map.of())
                        .allowed())
                .isFalse();
        var ended = resolver.resolve(contract, ITEM, END, Map.of());
        assertThat(ended.reason()).isEqualTo(ContractDecision.Reason.NO_EFFECTIVE_REVISION);
        assertThat(ended.configuration()).isNull();
        assertThat(resolver.resolve(contract, "basic.missing", START, Map.of()).reason())
                .isEqualTo(ContractDecision.Reason.PRODUCT_NOT_SIGNED);
    }

    @Test
    void suspensionAndTerminationOverrideHistoricalTimeQueries() {
        var revision = sign(plan(product(), Map.of(), Map.of(), Map.of()), Map.of(), START, END);
        var resolver = new ContractRuntimeResolver();
        assertThat(resolver.resolve(contract(revision, ContractStatus.SUSPENDED), ITEM, START, Map.of())
                        .reason())
                .isEqualTo(ContractDecision.Reason.SUSPENDED);
        assertThat(resolver.resolve(contract(revision, ContractStatus.TERMINATED), ITEM, START, Map.of())
                        .reason())
                .isEqualTo(ContractDecision.Reason.TERMINATED);
    }

    @Test
    void runtimeRejectsUnknownFactsAndChecksConfigurationReferencedLimit() {
        var amount = new ParameterDefinition(
                "limit", ValueType.INTEGER, true, TypedValue.integer(1000), null, null, Set.of(), Set.of());
        var usage = new Condition.Compare("transaction.amount", ComparisonOperator.LE, List.of(), "limit");
        var product =
                new ProductVersion("WECHAT", 1, "payment.collect", Map.of("limit", amount), usage, Set.of(), Set.of());
        var contract = contract(
                sign(plan(product, Map.of(), Map.of(), Map.of()), Map.of(), START, END), ContractStatus.NORMAL);
        var resolver = new ContractRuntimeResolver();
        assertThat(resolver.resolve(contract, ITEM, START, Map.of()).reason())
                .isEqualTo(ContractDecision.Reason.FACTS_INDETERMINATE);
        assertThat(resolver.resolve(contract, ITEM, START, Map.of("transaction.amount", TypedValue.integer(1001)))
                        .reason())
                .isEqualTo(ContractDecision.Reason.CONDITION_REJECTED);
        assertThat(resolver.resolve(contract, ITEM, START, Map.of("transaction.amount", TypedValue.integer(1000)))
                        .allowed())
                .isTrue();
    }

    @Test
    void overlappingOrMismatchedIdentityRevisionsCannotFormRuntimeView() {
        var first = sign(plan(product(), Map.of(), Map.of(), Map.of()), Map.of(), START, END);
        var overlap = new MerchantContractRevision(
                "M1",
                "STORE1",
                "C1",
                2,
                first.planCode(),
                first.planVersion(),
                START,
                START.plusSeconds(1),
                null,
                first.items());
        assertThatThrownBy(() ->
                        new MerchantContract("M1", "STORE1", "C1", ContractStatus.NORMAL, 2, List.of(first, overlap)))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("Overlapping");
        assertThatThrownBy(() -> new MerchantContract("M2", "STORE1", "C1", ContractStatus.NORMAL, 1, List.of(first)))
                .isInstanceOf(CommonException.class)
                .hasMessageContaining("identity");
        var adjacent = new MerchantContractRevision(
                "M1", "STORE1", "C1", 2, first.planCode(), first.planVersion(), START, END, null, first.items());
        var view = new MerchantContract("M1", "STORE1", "C1", ContractStatus.NORMAL, 2, List.of(adjacent, first));
        assertThat(new ContractRuntimeResolver()
                        .resolve(view, ITEM, END, Map.of())
                        .revision())
                .isEqualTo(2);
    }

    @Test
    void identicalContentsWithDifferentInsertionOrdersProduceIdenticalHashes() {
        var original = sign(plan(product(), Map.of(), Map.of(), Map.of()), Map.of(), START, END);
        var item = original.items().get(ITEM);
        var otherConfiguration = new ResolvedConfiguration(
                Map.of("extra", TypedValue.text("value"), "fee.rate", TypedValue.decimal("0.006")),
                Map.of(
                        "extra",
                        new ValueOrigin(ConfigurationLayer.PRODUCT, "WECHAT", 1),
                        "fee.rate",
                        new ValueOrigin(ConfigurationLayer.PRODUCT, "WECHAT", 1)));
        var orderedValues = new LinkedHashMap<String, TypedValue>();
        orderedValues.put("fee.rate", TypedValue.decimal("0.0060"));
        orderedValues.put("extra", TypedValue.text("value"));
        var orderedOrigins = new LinkedHashMap<String, ValueOrigin>();
        orderedOrigins.put("fee.rate", new ValueOrigin(ConfigurationLayer.PRODUCT, "WECHAT", 1));
        orderedOrigins.put("extra", new ValueOrigin(ConfigurationLayer.PRODUCT, "WECHAT", 1));
        var firstItem = new ResolvedContractItem(
                item.productCode(),
                item.productVersion(),
                item.capabilityCode(),
                otherConfiguration,
                item.usageCondition(),
                item.bundleOrigin());
        var secondItem = new ResolvedContractItem(
                item.productCode(),
                item.productVersion(),
                item.capabilityCode(),
                new ResolvedConfiguration(orderedValues, orderedOrigins),
                item.usageCondition(),
                item.bundleOrigin());
        var first = new MerchantContractRevision(
                "M1", "STORE1", "C1", 1, "STANDARD", 1, START, START, END, Map.of(ITEM, firstItem));
        var second = new MerchantContractRevision(
                "M1", "STORE1", "C1", 1, "STANDARD", 1, START, START, END, Map.of(ITEM, secondItem));
        assertThat(first.snapshotHash()).isEqualTo(second.snapshotHash());
    }
}
