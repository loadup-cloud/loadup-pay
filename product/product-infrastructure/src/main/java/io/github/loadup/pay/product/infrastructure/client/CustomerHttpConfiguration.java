package io.github.loadup.pay.product.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.customer.client.http.CustomerHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomerHttpConfiguration {
    @Bean
    public CustomerHttpService customerHttpService(HttpTemplate http) {
        return http.serviceClient("customer", CustomerHttpService.class);
    }
}
