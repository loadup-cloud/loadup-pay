/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.merchantmng;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.merchantcenter.client.http.MerchantManagementHttpService;
import io.github.loadup.productcenter.client.http.ProductManagementHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ManagementHttpConfiguration {
    @Bean
    public MerchantManagementHttpService merchantcenterManagementHttp(HttpTemplate http) {
        return http.serviceClient("merchantcenter", MerchantManagementHttpService.class);
    }

    @Bean
    public ProductManagementHttpService productcenterManagementHttp(HttpTemplate http) {
        return http.serviceClient("productcenter", ProductManagementHttpService.class);
    }
}
