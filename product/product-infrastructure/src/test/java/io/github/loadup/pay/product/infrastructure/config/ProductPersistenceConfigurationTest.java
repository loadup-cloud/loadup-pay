/*
 * #%L
 * LoadUp Pay Product Infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
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
