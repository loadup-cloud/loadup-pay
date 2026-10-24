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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.github.loadup.pay.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.RedirectActionDTO;
import io.github.loadup.pay.payment.client.enums.PaymentActionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentChannelInputConverterTest {
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentChannelInputConverter.class.getPackageName());
    private final PaymentChannelInputConverter converter = context.getBean(PaymentChannelInputConverter.class);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void convertsH5SceneInputToChannelInput() {
        var input = new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP);

        assertEquals(new ChannelH5Input("203.0.113.10", H5SceneType.WAP), converter.toH5(input));
    }

    @Test
    void convertsWechatH5ActionWithoutChangingRedirectUrlAndRetainsExpiry() {
        var redirectUrl = "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=synthetic";
        var expiresAt = OffsetDateTime.parse("2026-10-24T18:30:00+08:00");
        var action = new WechatH5ChannelAction(redirectUrl, expiresAt);

        var result = converter.toAction(action);

        assertEquals(PaymentActionType.REDIRECT, result.type());
        assertTrue(result.expiresAt().isEqual(expiresAt));
        var data = assertInstanceOf(RedirectActionDTO.class, result.data());
        assertEquals(redirectUrl, data.redirectUrl());
    }
}
