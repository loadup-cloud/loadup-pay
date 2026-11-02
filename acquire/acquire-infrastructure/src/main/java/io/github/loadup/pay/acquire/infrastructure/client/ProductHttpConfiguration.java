package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.product.client.http.ProductHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProductHttpConfiguration {
    @Bean
    public ProductHttpService productHttpService(HttpTemplate http) {
        return http.serviceClient("product", ProductHttpService.class);
    }
}
