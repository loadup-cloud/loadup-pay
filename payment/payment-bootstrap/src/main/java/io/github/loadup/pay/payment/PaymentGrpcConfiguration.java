/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment;

import com.google.protobuf.StringValue;
import io.github.loadup.pay.contract.PaymentRpc;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keeps the existing probe wire contract while delegating server lifecycle to Spring Boot. */
@Configuration(proxyBeanMethods = false)
public class PaymentGrpcConfiguration {
    @Bean
    BindableService paymentProbeService() {
        return () -> ServerServiceDefinition.builder(PaymentRpc.SERVICE)
                .addMethod(PaymentRpc.PING, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(StringValue.of("payment:" + request.getValue()));
                    observer.onCompleted();
                }))
                .build();
    }
}
