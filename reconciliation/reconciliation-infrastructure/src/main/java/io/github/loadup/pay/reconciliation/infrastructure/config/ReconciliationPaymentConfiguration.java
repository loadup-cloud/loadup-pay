package io.github.loadup.pay.reconciliation.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReconciliationPaymentProperties.class)
public class ReconciliationPaymentConfiguration {
}
