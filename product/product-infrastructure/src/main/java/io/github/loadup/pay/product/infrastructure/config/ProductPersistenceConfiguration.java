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
package io.github.loadup.pay.product.infrastructure.config;

import io.github.loadup.modules.contract.app.support.ContractCodec;
import io.github.loadup.modules.contract.infrastructure.converter.ContractStorageConverterImpl;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PolicyPublicationTransportProperties.class)
@MapperScan({
        "io.github.loadup.pay.product.infrastructure.mapper",
        "io.github.loadup.modules.contract.infrastructure.mapper"
})
@Import({ContractCodec.class, ContractStorageConverterImpl.class})
public class ProductPersistenceConfiguration {
}
