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
package io.github.loadup.pay.channel.wechat.config;

import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.wechat.WechatChannelProvider;
import io.github.loadup.pay.channel.wechat.WechatCodepayAdapter;
import io.github.loadup.pay.channel.wechat.WechatNotificationProvider;
import io.github.loadup.pay.channel.wechat.WechatPaymentAdapter;
import io.github.loadup.pay.channel.wechat.WechatRefundAdapter;
import io.github.loadup.pay.channel.wechat.WechatSubmitAdapter;
import io.github.loadup.pay.channel.wechat.protocol.WechatCredentialReader;
import io.github.loadup.pay.channel.wechat.protocol.WechatJsapiSigner;
import io.github.loadup.pay.channel.wechat.protocol.WechatNotificationCodec;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolClient;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolCodec;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2Client;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2Codec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Conditional V2 payer-code and V3 strategy assembly, with no dependency on the payment application.
 */
@org.springframework.context.annotation.ComponentScan(basePackageClasses = io.github.loadup.pay.channel.wechat.converter.WechatStatementConverter.class)
@AutoConfiguration(afterName = "io.github.loadup.pay.channel.core.config.ChannelAutoConfiguration")
@ConditionalOnProperty(prefix = "loadup.pay.channel.wechat", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({WechatProperties.class, WechatNotificationProperties.class, WechatCodepayProperties.class})
public class WechatChannelConfiguration {
    @Bean
    io.github.loadup.pay.channel.wechat.WechatStatementProvider wechatStatementProvider(HttpTemplate http, WechatCredentialReader credentials,
                                                                                        WechatProtocolCodec signatures, JsonMapper json, io.github.loadup.pay.channel.wechat.converter.WechatStatementConverter converter,
                                                                                        Clock clock) {
        return new io.github.loadup.pay.channel.wechat.WechatStatementProvider(http, credentials, signatures, json, converter, clock);
    }

    @Bean
    WechatV2Codec wechatV2Codec() {
        return new WechatV2Codec();
    }

    @Bean
    WechatV2Client wechatV2Client(HttpTemplate http, WechatCredentialReader credentials, WechatV2Codec codec) {
        return new WechatV2Client(http, credentials, codec);
    }

    @Bean
    WechatCodepayAdapter wechatCodepayAdapter(WechatV2Client client, WechatPaymentAdapter queries,
                                              WechatCodepayProperties properties, Clock clock) {
        return new WechatCodepayAdapter(client, queries, properties, clock);
    }

    @Bean
    WechatNotificationCodec wechatNotificationCodec(WechatCredentialReader credentials, WechatProtocolCodec signatures, JsonMapper json) {
        return new WechatNotificationCodec(credentials, signatures, json);
    }

    @Bean
    WechatNotificationProvider wechatNotificationProvider(WechatNotificationCodec codec) {
        return new WechatNotificationProvider(codec);
    }

    @Bean
    WechatJsapiSigner wechatJsapiSigner(WechatCredentialReader credentials, KmsTemplate kms, Clock clock) {
        return new WechatJsapiSigner(credentials, kms, clock);
    }

    @Bean
    WechatSubmitAdapter wechatSubmitAdapter(WechatProtocolClient client, WechatJsapiSigner signer,
                                            WechatNotificationProperties notifications, JsonMapper json, Clock clock) {
        return new WechatSubmitAdapter(client, signer, notifications, json, clock);
    }

    @Bean
    WechatPaymentAdapter wechatPaymentAdapter(WechatProtocolClient client, JsonMapper json, Clock clock) {
        return new WechatPaymentAdapter(client, json, clock);
    }

    @Bean
    WechatRefundAdapter wechatRefundAdapter(WechatProtocolClient client, JsonMapper json, Clock clock, WechatNotificationProperties notifications) {
        return new WechatRefundAdapter(client, json, clock, notifications);
    }

    @Bean
    WechatChannelProvider wechatChannelProvider(WechatSubmitAdapter submissions, WechatPaymentAdapter payments, WechatRefundAdapter refunds, WechatCodepayAdapter codepay) {
        return new WechatChannelProvider(submissions, payments, refunds, codepay);
    }

    @Bean
    WechatCredentialReader wechatCredentialReader(WechatProperties properties, JsonMapper json, KmsTemplate kms) {
        return new WechatCredentialReader(properties, json, kms);
    }

    @Bean
    WechatProtocolCodec wechatProtocolCodec(JsonMapper json, KmsTemplate kms, Clock clock) {
        return new WechatProtocolCodec(json, kms, clock);
    }

    @Bean
    WechatProtocolClient wechatProtocolClient(HttpTemplate http, WechatCredentialReader credentials, WechatProtocolCodec codec) {
        return new WechatProtocolClient(http, credentials, codec);
    }
}
