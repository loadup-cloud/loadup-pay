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
package io.github.loadup.pay.product.adapter.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductSecurityConfiguration implements InitializingBean {
    private final Environment environment;

    public ProductSecurityConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        if (!environment.getProperty("loadup.security.resource-server.enabled", Boolean.class, false))
            throw new IllegalStateException("Product business HTTP requires the verified JWT resource server");
        if (!environment.getProperty("loadup.outbox.enabled", Boolean.class, false))
            throw new IllegalStateException("Product publication requires the framework transactional Outbox");
        if (environment.getProperty("loadup.modules.contract.enabled", Boolean.class, true))
            throw new IllegalStateException(
                    "Product admission imports only the required framework contract storage and codec beans");
    }
}
