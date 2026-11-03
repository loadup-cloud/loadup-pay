/*
 * #%L
 * LoadUp Pay Reconciliation infrastructure
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
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
