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
import static org.mockito.Mockito.mock;

import io.github.loadup.productcenter.app.converter.ContractConverterImpl;
import io.github.loadup.productcenter.app.service.CatalogService;
import io.github.loadup.productcenter.app.service.ContractPersistenceService;
import io.github.loadup.productcenter.app.service.ContractResolveService;
import io.github.loadup.productcenter.app.service.MerchantContractService;
import io.github.loadup.productcenter.app.support.CatalogAssembler;
import io.github.loadup.productcenter.app.support.ContractCodec;
import io.github.loadup.productcenter.app.support.ContractMappingSupport;
import io.github.loadup.productcenter.app.support.MerchantFacts;
import io.github.loadup.productcenter.domain.gateway.CatalogGateway;
import io.github.loadup.productcenter.domain.gateway.MerchantContractGateway;
import io.github.loadup.productcenter.infrastructure.mapper.CatalogVersionRowMapper;
import io.github.loadup.productcenter.infrastructure.mapper.ContractRevisionRowMapper;
import io.github.loadup.productcenter.infrastructure.mapper.MerchantContractRowMapper;
import io.github.loadup.productcenter.infrastructure.repository.CatalogGatewayImpl;
import io.github.loadup.productcenter.infrastructure.repository.MerchantContractGatewayImpl;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

class ProductContractAssemblyTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ContractComponents.class)
            .withBean(Clock.class, Clock::systemDefaultZone)
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
            .withBean(CatalogVersionRowMapper.class, () -> mock(CatalogVersionRowMapper.class))
            .withBean(MerchantContractRowMapper.class, () -> mock(MerchantContractRowMapper.class))
            .withBean(ContractRevisionRowMapper.class, () -> mock(ContractRevisionRowMapper.class));

    @Test
    void componentScanAssemblesContractServicesAndRepositories() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CatalogService.class);
            assertThat(context).hasSingleBean(MerchantContractService.class);
            assertThat(context).hasSingleBean(ContractResolveService.class);
            assertThat(context).hasSingleBean(CatalogGateway.class);
            assertThat(context).hasSingleBean(MerchantContractGateway.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
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
    static class ContractComponents {}
}
