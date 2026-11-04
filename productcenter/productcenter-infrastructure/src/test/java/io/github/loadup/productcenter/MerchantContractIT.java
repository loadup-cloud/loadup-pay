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
package io.github.loadup.productcenter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.merchantcenter.app.converter.MerchantConverterImpl;
import io.github.loadup.merchantcenter.app.service.MerchantQueryService;
import io.github.loadup.merchantcenter.app.service.MerchantService;
import io.github.loadup.merchantcenter.client.command.MerchantCreateCommand;
import io.github.loadup.merchantcenter.client.command.MerchantStatusCommand;
import io.github.loadup.merchantcenter.client.command.MerchantUpdateCommand;
import io.github.loadup.merchantcenter.client.facade.MerchantQueryFacade;
import io.github.loadup.merchantcenter.client.query.MerchantPageQuery;
import io.github.loadup.merchantcenter.infrastructure.converter.MerchantStorageConverterImpl;
import io.github.loadup.merchantcenter.infrastructure.repository.MerchantGatewayImpl;
import io.github.loadup.productcenter.app.converter.ContractConverterImpl;
import io.github.loadup.productcenter.app.service.CatalogService;
import io.github.loadup.productcenter.app.service.ContractPersistenceService;
import io.github.loadup.productcenter.app.service.ContractResolveService;
import io.github.loadup.productcenter.app.service.MerchantContractService;
import io.github.loadup.productcenter.app.support.CatalogAssembler;
import io.github.loadup.productcenter.app.support.ContractCodec;
import io.github.loadup.productcenter.app.support.ContractMappingSupport;
import io.github.loadup.productcenter.app.support.MerchantFacts;
import io.github.loadup.productcenter.client.command.CatalogActionCommand;
import io.github.loadup.productcenter.client.command.CatalogSaveCommand;
import io.github.loadup.productcenter.client.command.ContractSignCommand;
import io.github.loadup.productcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.productcenter.client.dto.ValueDTO;
import io.github.loadup.productcenter.client.query.ContractResolveQuery;
import io.github.loadup.productcenter.client.spi.MerchantFactsProvider;
import io.github.loadup.productcenter.infrastructure.repository.CatalogGatewayImpl;
import io.github.loadup.productcenter.infrastructure.repository.MerchantContractGatewayImpl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MerchantContractIT.Application.class)
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
class MerchantContractIT {
    private final MerchantService merchants;
    private final MerchantQueryFacade lookup;
    private final MerchantFactsProvider facts;
    private final CatalogService catalog;
    private final MerchantContractService contracts;
    private final ContractResolveService runtime;

    @Autowired
    MerchantContractIT(
            MerchantService merchants,
            MerchantQueryFacade lookup,
            MerchantFactsProvider facts,
            CatalogService catalog,
            MerchantContractService contracts,
            ContractResolveService runtime) {
        this.merchants = merchants;
        this.lookup = lookup;
        this.facts = facts;
        this.catalog = catalog;
        this.contracts = contracts;
        this.runtime = runtime;
    }

    @Test
    void merchantCrudEnforcesUniqueCodeAndOptimisticVersion() {
        var created = merchants.create(command(), "operator");
        assertThatThrownBy(() -> merchants.create(command(), "operator")).isInstanceOf(CommonException.class);
        var updated = merchants.update(
                new MerchantUpdateCommand(
                        created.id(),
                        created.rowVersion(),
                        "M001",
                        "Updated",
                        null,
                        "ENTERPRISE",
                        "SERVICES",
                        "CN",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "operator");
        assertThat(updated.contactPhone()).isEqualTo("13800138000");
        assertThat(facts.load(created.id()).facts().get("merchant.industry").value())
                .isEqualTo("SERVICES");
        assertThatThrownBy(() -> merchants.changeStatus(
                        new MerchantStatusCommand(created.id(), created.rowVersion(), "INACTIVE"), "operator"))
                .isInstanceOf(CommonException.class);
        assertThat(merchants
                        .page(new MerchantPageQuery(null, null, null, 1, 20))
                        .getPageInfo()
                        .totalCount())
                .isEqualTo(1);
        assertThat(lookup.find(created.id())).isPresent();
    }

    @Test
    void contractUsesPersistedIndustryAndDisabledMerchantIsRejected() {
        var merchant = merchants.create(command(), "operator");
        String plan = plan();
        var sign = new ContractSignCommand(
                merchant.id(), "store", plan, "request", Set.of("basic.scan"), Map.of(), null, null);
        var signed = contracts.sign(sign, "operator");
        var query = new ContractResolveQuery(merchant.id(), "store", "basic.scan", Map.of());
        assertThat(runtime.resolve(query).allowed()).isTrue();
        var inactive = merchants.changeStatus(
                new MerchantStatusCommand(merchant.id(), merchant.rowVersion(), "INACTIVE"), "operator");
        assertThatThrownBy(() -> contracts.preview(
                        new ContractSignCommand(
                                merchant.id(), "second", plan, "second", Set.of("basic.scan"), Map.of(), null, null),
                        "operator"))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> runtime.resolve(query)).isInstanceOf(CommonException.class);
        assertThat(contracts.sign(sign, "operator").id()).isEqualTo(signed.id());
        var active = merchants.changeStatus(
                new MerchantStatusCommand(merchant.id(), inactive.rowVersion(), "ACTIVE"), "operator");
        assertThat(runtime.resolve(query).allowed()).isTrue();
        merchants.update(
                new MerchantUpdateCommand(
                        merchant.id(),
                        active.rowVersion(),
                        "M001",
                        "Retail",
                        null,
                        "ENTERPRISE",
                        "SERVICES",
                        "CN",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                "operator");
        assertThat(runtime.resolve(query).allowed()).isFalse();
        assertThat(contracts.detail(signed.id()).snapshotHash()).isEqualTo(signed.snapshotHash());
    }

    private MerchantCreateCommand command() {
        return new MerchantCreateCommand(
                "M001",
                "Retail",
                null,
                "ENTERPRISE",
                "RETAIL",
                "CN",
                null,
                null,
                "Address",
                "REG001",
                "Contact",
                "13800138000",
                "test@example.com");
    }

    private String publish(String kind, String code, Map<String, Object> definition) {
        var draft = catalog.save(new CatalogSaveCommand(null, null, kind, code, 1, definition), "operator");
        return catalog.publish(new CatalogActionCommand(draft.id(), draft.rowVersion()), "operator")
                .id();
    }

    private String plan() {
        String rule = publish(
                "CONDITION",
                "RETAIL_ONLY",
                Map.of(
                        "mode",
                        "ALL",
                        "clauses",
                        List.of(Map.of(
                                "kind",
                                "COMPARE",
                                "negated",
                                false,
                                "field",
                                "merchant.industry",
                                "operator",
                                "EQ",
                                "valueType",
                                "STRING",
                                "expected",
                                List.of("RETAIL")))));
        String product = publish(
                "PRODUCT",
                "SCAN",
                Map.of(
                        "capabilityCode",
                        "payment.collect",
                        "parameters",
                        List.of(),
                        "conditionId",
                        rule,
                        "dependencies",
                        List.of(),
                        "exclusions",
                        List.of()));
        String bundle = publish(
                "BUNDLE",
                "BASIC",
                Map.of(
                        "items",
                        List.of(Map.of(
                                "itemKey",
                                "scan",
                                "productId",
                                product,
                                "required",
                                true,
                                "defaultSelected",
                                true,
                                "values",
                                Map.of()))));
        return publish(
                "SALES_PLAN",
                "STANDARD",
                Map.of(
                        "bundles",
                        List.of(Map.of("alias", "basic", "bundleId", bundle)),
                        "values",
                        Map.of(),
                        "merchantPolicies",
                        Map.of(),
                        "eligibilityConditionId",
                        rule,
                        "saleStartsAt",
                        Instant.now().minusSeconds(60).toString()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @org.springframework.context.annotation.Import({
        CatalogService.class,
        MerchantContractService.class,
        ContractResolveService.class,
        ContractPersistenceService.class,
        CatalogAssembler.class,
        ContractCodec.class,
        ContractMappingSupport.class,
        MerchantFacts.class,
        ContractConverterImpl.class,
        CatalogGatewayImpl.class,
        MerchantContractGatewayImpl.class,
        MerchantService.class,
        MerchantQueryService.class,
        MerchantConverterImpl.class,
        MerchantGatewayImpl.class,
        MerchantStorageConverterImpl.class
    })
    static class Application {
        @org.springframework.context.annotation.Bean
        MerchantFactsProvider merchantFacts(MerchantQueryFacade lookup) {
            return id -> lookup.find(id)
                    .map(profile -> new MerchantProfileDTO(
                            profile.id(),
                            profile.active(),
                            Map.of(
                                    "merchant.code", new ValueDTO("STRING", profile.merchantCode()),
                                    "merchant.type", new ValueDTO("STRING", profile.type()),
                                    "merchant.industry", new ValueDTO("STRING", profile.industry()),
                                    "merchant.country", new ValueDTO("STRING", profile.country()))))
                    .orElse(null);
        }
    }
}
