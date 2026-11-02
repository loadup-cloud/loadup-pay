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

import io.github.loadup.components.rpc.dubbo.RpcClientDefaults;
import io.github.loadup.modules.contract.app.service.ContractPersistenceService;
import io.github.loadup.modules.contract.app.support.ContractCodec;
import io.github.loadup.modules.contract.infrastructure.repository.MerchantContractGatewayImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Import({ContractCodec.class, ContractPersistenceService.class, MerchantContractGatewayImpl.class})
public class ProductPersistenceConfiguration {
    @Bean
    public PolicyPublicationTransportProperties policyPublicationTransportProperties(Environment environment) {
        return RpcClientDefaults.bind(
                environment,
                "loadup.modules.product.publication",
                "limitcenter",
                PolicyPublicationTransportProperties.class);
    }
}
