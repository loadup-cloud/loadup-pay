/* Copyright 2026 LoadUp Cloud. Licensed under the Apache License, Version 2.0. */
package io.github.loadup.pay.product.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.loadup.modules.contract.app.service.CatalogService;
import io.github.loadup.modules.contract.app.service.MerchantContractService;
import io.github.loadup.modules.contract.client.facade.ContractPersistenceFacade;
import io.github.loadup.modules.contract.domain.gateway.MerchantContractGateway;
import io.github.loadup.modules.contract.infrastructure.mapper.ContractRevisionRowMapper;
import io.github.loadup.modules.contract.infrastructure.mapper.MerchantContractRowMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class ProductPersistenceConfigurationTest {
    @Test
    void suppliesContractPersistenceWhileFullContractModuleIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProductPersistenceConfiguration.class)
                .withPropertyValues("spring.application.name=product", "loadup.modules.contract.enabled=false")
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class))
                .withBean(MerchantContractRowMapper.class, () -> mock(MerchantContractRowMapper.class))
                .withBean(ContractRevisionRowMapper.class, () -> mock(ContractRevisionRowMapper.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ContractPersistenceFacade.class);
                    assertThat(context).hasSingleBean(MerchantContractGateway.class);
                    assertThat(context).doesNotHaveBean(CatalogService.class);
                    assertThat(context).doesNotHaveBean(MerchantContractService.class);
                });
    }
}
