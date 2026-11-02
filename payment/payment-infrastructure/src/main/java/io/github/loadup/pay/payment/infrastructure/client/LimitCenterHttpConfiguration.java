package io.github.loadup.pay.payment.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.limitcenter.client.http.LimitCenterHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class LimitCenterHttpConfiguration {
    @Bean
    public LimitCenterHttpService limitCenterHttpService(HttpTemplate http) {
        return http.serviceClient("limitcenter", LimitCenterHttpService.class);
    }
}
