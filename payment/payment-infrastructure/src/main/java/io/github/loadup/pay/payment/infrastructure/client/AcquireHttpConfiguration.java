package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.acquire.client.http.AcquireHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AcquireHttpConfiguration {
    @Bean
    public AcquireHttpService acquireHttpService(HttpTemplate http) {
        return http.serviceClient("acquire", AcquireHttpService.class);
    }
}
