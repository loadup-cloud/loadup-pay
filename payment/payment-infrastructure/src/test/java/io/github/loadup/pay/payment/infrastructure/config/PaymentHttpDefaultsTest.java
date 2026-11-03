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
package io.github.loadup.pay.payment.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PaymentHttpDefaultsTest {
    @Test
    void bindsActualPaymentSettingsWithoutPerTargetProperties() {
        var environment = new MockEnvironment().withProperty("spring.application.name", "payment");
        var settings = new PaymentPersistenceConfiguration().paymentLimitProperties(environment);
        assertThat(settings.tenantId()).isEqualTo("__default__");
        assertThat(settings.serviceId()).isEqualTo("payment");
        assertThat(settings.audience()).isEqualTo("limitcenter");
    }

    @Test
    void invalidOverrideIdentifiesTheFieldWithoutDisclosingItsValue() {
        var environment = new MockEnvironment()
                .withProperty("spring.application.name", "payment")
                .withProperty("loadup.modules.payment.limitcenter.tenant-id", "");
        assertThatThrownBy(() -> new PaymentPersistenceConfiguration().paymentLimitProperties(environment))
                .hasRootCauseMessage("loadup.modules.payment.limitcenter.tenant-id is missing or blank");
    }
}
