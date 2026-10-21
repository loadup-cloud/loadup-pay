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
package io.github.loadup.pay.acquire;

import com.google.protobuf.StringValue;
import io.github.loadup.pay.contract.PaymentRpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.stub.ClientCalls;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

/** Independent dependency probe; channel lifecycle is owned by the native gRPC starter. */
@Component("paymentRpc")
public final class PaymentProbe implements HealthIndicator {
    private final Channel channel;

    public PaymentProbe(GrpcChannelFactory channels) {
        channel = channels.createChannel("payment");
    }

    @Override
    public Health health() {
        try {
            String reply = ClientCalls.blockingUnaryCall(
                            channel,
                            PaymentRpc.PING,
                            CallOptions.DEFAULT.withDeadlineAfter(3, TimeUnit.SECONDS),
                            StringValue.of("acquire"))
                    .getValue();
            if (!"payment:acquire".equals(reply)) return Health.down().build();
            return Health.up().withDetail("reply", reply).build();
        } catch (RuntimeException failure) {
            return Health.down().build();
        }
    }
}
