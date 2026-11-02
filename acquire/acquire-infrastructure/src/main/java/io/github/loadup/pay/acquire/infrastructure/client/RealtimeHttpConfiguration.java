package io.github.loadup.pay.acquire.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.realtime.client.http.RealtimeHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RealtimeHttpConfiguration {
    @Bean
    public RealtimeHttpService realtimeHttpService(HttpTemplate http) {
        return http.serviceClient("realtime", RealtimeHttpService.class);
    }
}
