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
package io.github.loadup.notificationcenter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class NotificationCenterSecurityConfigurationTest {
    @Test
    void rejectsWhenResourceServerIsDisabled() {
        var environment = new MockEnvironment().withProperty("loadup.database.multi-tenant.enabled", "false");
        var configuration = new NotificationCenterSecurityConfiguration(environment);

        assertThatThrownBy(configuration::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Notification center requires verified JWT and single-tenant database mode");
    }

    @Test
    void rejectsMultiTenantMode() {
        var environment = new MockEnvironment()
                .withProperty("loadup.security.resource-server.enabled", "true")
                .withProperty("loadup.database.multi-tenant.enabled", "true");
        var configuration = new NotificationCenterSecurityConfiguration(environment);

        assertThatThrownBy(configuration::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Notification center requires verified JWT and single-tenant database mode");
    }

    @Test
    void acceptsResourceServerWithSingleTenantMode() {
        var environment = new MockEnvironment()
                .withProperty("loadup.security.resource-server.enabled", "true")
                .withProperty("loadup.database.multi-tenant.enabled", "false");
        var configuration = new NotificationCenterSecurityConfiguration(environment);

        assertThatCode(configuration::afterPropertiesSet).doesNotThrowAnyException();
    }
}
