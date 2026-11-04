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
package io.github.loadup.acquire.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.DomainViolation;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

class HttpPropertiesValidationTest {
    @Test
    void immutableSettingsRemainUnproxiedWhenMethodValidationIsEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(MethodValidationConfiguration.class)
                .withBean(PaymentTransportProperties.class, () -> new PaymentTransportProperties("acquire", "payment"))
                .withBean(AcquireRealtimeProperties.class, () -> new AcquireRealtimeProperties("acquire", "realtime"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(AopUtils.isAopProxy(context.getBean(PaymentTransportProperties.class)))
                            .isFalse();
                    assertThat(AopUtils.isAopProxy(context.getBean(AcquireRealtimeProperties.class)))
                            .isFalse();
                });
    }

    @Test
    void constructorStillRejectsInvalidSettings() {
        assertThatThrownBy(() -> new PaymentTransportProperties("", "payment")).isInstanceOf(DomainViolation.class);
    }

    @Configuration(proxyBeanMethods = false)
    static class MethodValidationConfiguration {
        @Bean
        static MethodValidationPostProcessor methodValidationPostProcessor() {
            var processor = new MethodValidationPostProcessor();
            processor.setProxyTargetClass(true);
            return processor;
        }
    }
}
