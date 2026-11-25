/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
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
 */
package io.github.loadup.components.payment.channel.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.loadup.components.payment.channel.api.codec.ChannelActionCodec;
import io.github.loadup.components.payment.channel.api.codec.ChannelActionTypeCodec;
import io.github.loadup.components.payment.channel.api.facade.ChannelNotificationFacade;
import io.github.loadup.components.payment.channel.api.facade.ChannelPaymentFacade;
import io.github.loadup.components.payment.channel.api.facade.ChannelStatementFacade;
import io.github.loadup.components.payment.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.components.payment.channel.api.spi.ChannelProvider;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.core.config.PaymentChannelAutoConfiguration;
import io.github.loadup.components.resilience4j.ResilienceRegistries;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentChannelAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentChannelAutoConfiguration.class))
            .withBean(ResilienceRegistries.class, PaymentChannelAutoConfigurationTest::registries)
            .withBean(ChannelProvider.class, () -> {
                var provider = mock(ChannelProvider.class);
                when(provider.channel()).thenReturn(Channel.ALIPAY);
                return provider;
            })
            .withBean("syntheticActionCodec", ChannelActionTypeCodec.class, SyntheticActionCodec::new);

    @Test
    void registersDefaultFacadesCodecAndRecoveryConcurrency() {
        runner.run(context -> {
            assertNotNull(context.getBean(ChannelPaymentFacade.class));
            assertNotNull(context.getBean(ChannelStatementFacade.class));
            assertNotNull(context.getBean(ChannelNotificationFacade.class));
            var actionCodec = context.getBean(ChannelActionCodec.class);
            assertNotNull(actionCodec);
            var action = new AlipayJsapiChannelAction("trade-1", OffsetDateTime.parse("2030-01-01T12:00:00+08:00"));
            Assertions.assertEquals("ALIPAY_JSAPI", actionCodec.kind(action));
            var payload = actionCodec.encode(action);
            Assertions.assertEquals(action, actionCodec.decode("ALIPAY_JSAPI", payload, actionCodec.checksum(payload)));
            assertNotNull(context.getBean(ChannelRecoveryConcurrency.class));
        });
    }

    private static final class SyntheticActionCodec implements ChannelActionTypeCodec<AlipayJsapiChannelAction> {
        @Override
        public String kind() {
            return "ALIPAY_JSAPI";
        }

        @Override
        public Class<AlipayJsapiChannelAction> actionType() {
            return AlipayJsapiChannelAction.class;
        }

        @Override
        public String encode(AlipayJsapiChannelAction action) {
            return "trade-1";
        }

        @Override
        public AlipayJsapiChannelAction decode(String payload) {
            return new AlipayJsapiChannelAction("trade-1", OffsetDateTime.parse("2030-01-01T12:00:00+08:00"));
        }
    }

    private static ResilienceRegistries registries() {
        var registries = ResilienceRegistries.ofDefaults();
        for (var channel : Channel.values()) {
            registries
                    .bulkheadRegistry()
                    .bulkhead(
                            ChannelRecoveryConcurrency.instanceName(channel),
                            BulkheadConfig.custom()
                                    .maxConcurrentCalls(1)
                                    .maxWaitDuration(Duration.ZERO)
                                    .build());
        }
        return registries;
    }
}
