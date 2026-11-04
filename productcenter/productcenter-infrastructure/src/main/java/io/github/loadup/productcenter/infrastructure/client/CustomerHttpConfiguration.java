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
package io.github.loadup.productcenter.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.merchantcenter.client.http.CustomerHttpService;
import io.github.loadup.merchantcenter.client.http.MerchantProfileHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomerHttpConfiguration {
    @Bean
    public MerchantProfileHttpService merchantProfileHttpService(HttpTemplate http) {
        return http.serviceClient("merchantcenter", MerchantProfileHttpService.class);
    }

    @Bean
    public CustomerHttpService customerHttpService(HttpTemplate http) {
        return http.serviceClient("merchantcenter", CustomerHttpService.class);
    }
}
