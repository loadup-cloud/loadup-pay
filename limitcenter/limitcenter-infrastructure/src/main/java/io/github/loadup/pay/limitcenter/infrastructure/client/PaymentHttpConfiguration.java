package io.github.loadup.pay.limitcenter.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.payment.client.http.PaymentHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentHttpConfiguration {
    @Bean
    public PaymentHttpService paymentHttpService(HttpTemplate http) {
        return http.serviceClient("payment", PaymentHttpService.class);
    }
}
