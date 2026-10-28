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
package io.github.loadup.pay.channel.alipay.config;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.alipay.AlipayChannelProvider;
import io.github.loadup.pay.channel.alipay.AlipayNotificationProvider;
import io.github.loadup.pay.channel.alipay.AlipayPaymentAdapter;
import io.github.loadup.pay.channel.alipay.AlipayRefundAdapter;
import io.github.loadup.pay.channel.alipay.AlipaySubmitAdapter;
import io.github.loadup.pay.channel.alipay.protocol.AlipayCredentialReader;
import io.github.loadup.pay.channel.alipay.protocol.AlipayNotificationCodec;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolClient;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolCodec;
import io.github.loadup.pay.channel.alipay.protocol.AlipayStatementGrantClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@AutoConfiguration(afterName = "io.github.loadup.pay.channel.core.config.ChannelAutoConfiguration")
@ConditionalOnProperty(prefix = "loadup.pay.channel.alipay", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({AlipayProperties.class, AlipayNotificationProperties.class, AlipayH5Properties.class})
public class AlipayChannelConfiguration {
    @Bean
    AlipayStatementGrantClient alipayStatementGrantClient(AlipayProtocolClient client, JsonMapper json, @Qualifier("channelClock") Clock clock) {
        return new AlipayStatementGrantClient(client, json, clock);
    }

    @Bean
    AlipayNotificationCodec alipayNotificationCodec(AlipayCredentialReader credentials) {
        return new AlipayNotificationCodec(credentials);
    }

    @Bean
    AlipayNotificationProvider alipayNotificationProvider(AlipayNotificationCodec codec) {
        return new AlipayNotificationProvider(codec);
    }

    @Bean
    AlipayRefundAdapter alipayRefundAdapter(AlipayProtocolClient client, JsonMapper json, @Qualifier("channelClock") Clock clock) {
        return new AlipayRefundAdapter(client, json, clock);
    }

    @Bean
    AlipayPaymentAdapter alipayPaymentAdapter(AlipayProtocolClient client, JsonMapper json, @Qualifier("channelClock") Clock clock) {
        return new AlipayPaymentAdapter(client, json, clock);
    }

    @Bean
    AlipaySubmitAdapter alipaySubmitAdapter(AlipayProtocolClient client, AlipayPaymentAdapter queries, JsonMapper json,
                                            @Qualifier("channelClock") Clock clock, AlipayNotificationProperties notifications, AlipayH5Properties h5) {
        return new AlipaySubmitAdapter(client, queries, json, clock, notifications, h5);
    }

    @Bean
    AlipayChannelProvider alipayChannelProvider(AlipayRefundAdapter refunds, AlipayPaymentAdapter payments, AlipaySubmitAdapter submissions) {
        return new AlipayChannelProvider(refunds, payments, submissions);
    }

    @Bean
    AlipayCredentialReader alipayCredentialReader(AlipayProperties properties, JsonMapper json, KmsTemplate kms) {
        return new AlipayCredentialReader(properties, json, kms);
    }

    @Bean
    AlipayProtocolCodec alipayProtocolCodec(JsonMapper json, KmsTemplate kms, @Qualifier("channelClock") Clock clock) {
        return new AlipayProtocolCodec(json, kms, clock);
    }

    @Bean
    AlipayProtocolClient alipayProtocolClient(HttpTemplate http, AlipayCredentialReader credentials, AlipayProtocolCodec codec) {
        return new AlipayProtocolClient(http, credentials, codec);
    }
}
