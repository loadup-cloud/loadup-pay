package io.github.loadup.pay.acquire.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .withBean(
                        PaymentTransportProperties.class,
                        () -> new PaymentTransportProperties("__default__", "acquire", "payment"))
                .withBean(
                        AcquireRealtimeProperties.class,
                        () -> new AcquireRealtimeProperties("__default__", "acquire", "realtime"))
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
        assertThatThrownBy(() -> new PaymentTransportProperties("", "acquire", "payment"))
                .isInstanceOf(io.github.loadup.commons.error.DomainViolation.class);
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
