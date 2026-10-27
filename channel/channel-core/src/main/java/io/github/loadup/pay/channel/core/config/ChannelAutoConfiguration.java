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
package io.github.loadup.pay.channel.core.config;

import io.github.loadup.pay.channel.api.codec.ChannelActionCodec;
import io.github.loadup.pay.channel.api.facade.ChannelPaymentFacade;
import io.github.loadup.pay.channel.api.facade.ChannelNotificationFacade;
import io.github.loadup.pay.channel.api.spi.ChannelNotificationProvider;
import io.github.loadup.pay.channel.core.DefaultChannelNotificationFacade;
import io.github.loadup.pay.channel.api.spi.ChannelProvider;
import io.github.loadup.pay.channel.core.DefaultChannelPaymentFacade;
import io.github.loadup.pay.channel.core.ChannelRecoveryConcurrency;
import io.github.loadup.components.resilience4j.ResilienceRegistries;
import io.github.loadup.pay.channel.core.codec.DefaultChannelActionCodec;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@AutoConfiguration(afterName = "io.github.loadup.components.resilience4j.core.Resilience4jCoreAutoConfiguration")
public class ChannelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ChannelPaymentFacade.class)
    ChannelPaymentFacade channelPaymentFacade(ObjectProvider<ChannelProvider> providers, ChannelRecoveryConcurrency recovery) {
        return new DefaultChannelPaymentFacade(providers.orderedStream().toList(), recovery);
    }

    @Bean
    @ConditionalOnMissingBean(ChannelRecoveryConcurrency.class)
    ChannelRecoveryConcurrency channelRecoveryConcurrency(ResilienceRegistries registries) {
        return new ChannelRecoveryConcurrency(registries);
    }

    @Bean
    @ConditionalOnMissingBean(ChannelNotificationFacade.class)
    ChannelNotificationFacade channelNotificationFacade(ObjectProvider<ChannelNotificationProvider> providers) {
        return new DefaultChannelNotificationFacade(providers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean(ChannelActionCodec.class)
    ChannelActionCodec channelActionCodec(JsonMapper json) {
        return new DefaultChannelActionCodec(json);
    }

    @Bean
    @ConditionalOnMissingBean(name = "channelClock")
    Clock channelClock() {
        return Clock.systemDefaultZone();
    }
}

