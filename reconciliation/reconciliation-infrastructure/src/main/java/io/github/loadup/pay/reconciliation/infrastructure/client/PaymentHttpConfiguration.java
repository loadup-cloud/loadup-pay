package io.github.loadup.pay.reconciliation.infrastructure.client;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.payment.client.http.PaymentCaptureHttpService;
import io.github.loadup.pay.payment.client.http.PaymentStatementHttpService;
import io.github.loadup.pay.payment.client.http.PaymentVerificationHttpService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentHttpConfiguration {
    @Bean
    public PaymentCaptureHttpService paymentCaptureHttpService(HttpTemplate http) {
        return http.serviceClient("payment", PaymentCaptureHttpService.class);
    }
    @Bean
    public PaymentStatementHttpService paymentStatementHttpService(HttpTemplate http) {
        return http.serviceClient("payment-statements", PaymentStatementHttpService.class);
    }
    @Bean
    public PaymentVerificationHttpService paymentVerificationHttpService(HttpTemplate http) {
        return http.serviceClient("payment", PaymentVerificationHttpService.class);
    }
}
