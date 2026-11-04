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
import io.github.loadup.productcenter.client.command.ContractStatusCommand;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.dto.MerchantProfileDTO;
import io.github.loadup.productcenter.client.dto.ValueDTO;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.query.ContractResolveQuery;
import io.github.loadup.productcenter.client.spi.MerchantFactsProvider;
import io.github.loadup.productcenter.infrastructure.repository.CatalogGatewayImpl;
import io.github.loadup.productcenter.infrastructure.repository.MerchantContractGatewayImpl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = ContractPersistenceIT.Application.class)
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
class ContractPersistenceIT {
    private final CatalogService catalog;
    private final MerchantContractService contracts;
    private final ContractResolveService runtime;
    private final JdbcTemplate jdbc;

    @Autowired
    ContractPersistenceIT(
            CatalogService catalog,
            MerchantContractService contracts,
            ContractResolveService runtime,
            JdbcTemplate jdbc) {
        this.catalog = catalog;
        this.contracts = contracts;
        this.runtime = runtime;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM merchant_contract_revision");
        jdbc.update("DELETE FROM merchant_contract");
        jdbc.update("DELETE FROM contract_catalog_version");
    }

    @Test
    void publishedVersionsCannotBeEditedAndMissingReferencesAreRejected() {
        CatalogVersionDTO product = product();
        assertThatThrownBy(() -> catalog.save(
                        new CatalogSaveCommand(
                                product.id(), product.rowVersion(), "PRODUCT", product.code(), 1, product.definition()),
                        "operator"))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> catalog.save(
                        new CatalogSaveCommand(
                                null,
                                null,
                                "BUNDLE",
                                "BASIC",
                                1,
                                bundleDefinition(UUID.randomUUID().toString())),
                        "operator"))
                .isInstanceOf(CommonException.class);
    }

    @Test
    void staleDraftAndStateGenerationsAreRejected() {
        var draft =
                catalog.save(new CatalogSaveCommand(null, null, "PRODUCT", "SCAN", 1, productDefinition()), "operator");
        var updated = catalog.save(
                new CatalogSaveCommand(draft.id(), draft.rowVersion(), "PRODUCT", "SCAN", 1, productDefinition()),
                "operator");
        assertThatThrownBy(() -> catalog.publish(new CatalogActionCommand(draft.id(), draft.rowVersion()), "operator"))
                .isInstanceOf(CommonException.class);
        assertThat(catalog.publish(new CatalogActionCommand(updated.id(), updated.rowVersion()), "operator")
                        .status())
                .isEqualTo("PUBLISHED");
    }

    @Test
    void signReplaysDurablyAndSuspensionImmediatelyDeniesRuntime() {
        String plan = plan();
        var command = command(plan, "request");
        var signed = contracts.sign(command, "operator");
        assertThat(contracts.sign(command, "operator").id()).isEqualTo(signed.id());
        assertThatThrownBy(() -> contracts.sign(
                        new ContractSignCommand(
                                "merchant", "other", plan, "request", Set.of("basic.scan"), Map.of(), null, null),
                        "operator"))
                .isInstanceOf(CommonException.class);
        assertThat(runtime.resolve(new ContractResolveQuery("merchant", "store", "basic.scan", Map.of()))
                        .allowed())
                .isTrue();
        var suspended = contracts.changeStatus(
                new ContractStatusCommand(signed.id(), signed.generation(), "SUSPENDED"), "operator");
        assertThat(runtime.resolve(new ContractResolveQuery("merchant", "store", "basic.scan", Map.of()))
                        .reason())
                .isEqualTo("SUSPENDED");
        assertThatThrownBy(() -> contracts.changeStatus(
                        new ContractStatusCommand(signed.id(), signed.generation(), "NORMAL"), "operator"))
                .isInstanceOf(CommonException.class);
        var terminated = contracts.changeStatus(
                new ContractStatusCommand(signed.id(), suspended.generation(), "TERMINATED"), "operator");
        assertThatThrownBy(() -> contracts.changeStatus(
                        new ContractStatusCommand(signed.id(), terminated.generation(), "NORMAL"), "operator"))
                .isInstanceOf(CommonException.class);
    }

    @Test
    void concurrentDuplicateSigningReturnsOneCommittedContract() throws Exception {
        String plan = plan();
        var command = command(plan, "concurrent");
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return contracts.sign(command, "operator");
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return contracts.sign(command, "operator");
            });
            String id = first.get(30, TimeUnit.SECONDS).id();
            assertThat(second.get(30, TimeUnit.SECONDS).id()).isEqualTo(id);
            assertThat(contracts.sign(command, "operator").id()).isEqualTo(id);
            assertThatThrownBy(() -> contracts.sign(command(plan, "different-request"), "operator"))
                    .isInstanceOfSatisfying(
                            CommonException.class,
                            failure -> assertThat(failure.getResultCode()).isEqualTo(ContractResultCodeEnum.CONFLICT));
            assertThat(contracts.detail(id).id()).isEqualTo(id);
        }
    }

    @Test
    void failedSigningLeavesNoHeaderAndRetirementDoesNotRewriteExistingTerms() {
        String plan = plan();
        var invalid =
                new ContractSignCommand("merchant", "store", plan, "invalid", Set.of("missing"), Map.of(), null, null);
        assertThatThrownBy(() -> contracts.sign(invalid, "operator")).isInstanceOf(CommonException.class);
        var signed = contracts.sign(command(plan, "valid"), "operator");
        var version = catalog.detail(plan);
        catalog.retire(new CatalogActionCommand(plan, version.rowVersion()), "operator");
        assertThat(contracts.detail(signed.id()).snapshotHash()).isEqualTo(signed.snapshotHash());
        assertThat(runtime.resolve(new ContractResolveQuery("merchant", "store", "basic.scan", Map.of()))
                        .allowed())
                .isTrue();
    }

    private ContractSignCommand command(String plan, String request) {
        return new ContractSignCommand("merchant", "store", plan, request, Set.of("basic.scan"), Map.of(), null, null);
    }

    private CatalogVersionDTO product() {
        return publish("PRODUCT", "SCAN", productDefinition());
    }

    private String plan() {
        var product = product();
        var bundle = publish("BUNDLE", "BASIC", bundleDefinition(product.id()));
        return publish(
                        "SALES_PLAN",
                        "STANDARD",
                        Map.of(
                                "bundles",
                                List.of(Map.of("alias", "basic", "bundleId", bundle.id())),
                                "values",
                                Map.of(),
                                "merchantPolicies",
                                Map.of(),
                                "saleStartsAt",
                                Instant.now().minusSeconds(60).toString()))
                .id();
    }

    private CatalogVersionDTO publish(String kind, String code, Map<String, Object> definition) {
        var draft = catalog.save(new CatalogSaveCommand(null, null, kind, code, 1, definition), "operator");
        return catalog.publish(new CatalogActionCommand(draft.id(), draft.rowVersion()), "operator");
    }

    private Map<String, Object> productDefinition() {
        return Map.of(
                "capabilityCode",
                "payment.collect",
                "parameters",
                List.of(Map.of(
                        "key",
                        "fee.rate",
                        "type",
                        "DECIMAL",
                        "required",
                        true,
                        "defaultValue",
                        "0.005",
                        "minimum",
                        "0.003",
                        "maximum",
                        "0.01",
                        "allowedValues",
                        List.of(),
                        "editableLayers",
                        List.of("BUNDLE", "SALES_PLAN", "MERCHANT"))),
                "dependencies",
                List.of(),
                "exclusions",
                List.of());
    }

    private Map<String, Object> bundleDefinition(String productId) {
        return Map.of(
                "items",
                List.of(Map.of(
                        "itemKey",
                        "scan",
                        "productId",
                        productId,
                        "required",
                        true,
                        "defaultSelected",
                        true,
                        "values",
                        Map.of())));
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
        MerchantContractGatewayImpl.class
    })
    static class Application {
        @Bean
        MerchantFactsProvider verifiedMerchants() {
            return merchant -> new MerchantProfileDTO(
                    merchant, true, Map.of("merchant.industry", new ValueDTO("STRING", "RETAIL")));
        }
    }
}
