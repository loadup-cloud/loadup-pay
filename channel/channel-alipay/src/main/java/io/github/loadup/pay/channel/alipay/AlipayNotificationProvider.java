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
package io.github.loadup.pay.channel.alipay;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.api.spi.ChannelNotificationProvider;
import io.github.loadup.pay.channel.alipay.protocol.AlipayNotificationCodec;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;

/** Alipay trade notification strategy; acknowledgements are fixed protocol values. */
public final class AlipayNotificationProvider implements ChannelNotificationProvider {
    private final AlipayNotificationCodec codec;
    public AlipayNotificationProvider(AlipayNotificationCodec codec) { this.codec = codec; }
    @Override public Channel channel() { return Channel.ALIPAY; }
    @Override public ChannelNotificationHint identify(Channel channel, ChannelNotificationInput input) {
        if (channel != channel()) throw new CommonException(INVALID_ARGUMENT);
        return codec.identify(input);
    }
    @Override public VerifiedChannelNotification verify(ChannelExecutionContext context, ChannelPaymentTarget target,
            ChannelNotificationInput input) { return codec.verify(context, target, input); }
    @Override public ChannelNotificationAcknowledgement acknowledgement(Channel channel, boolean accepted) {
        if (channel != channel()) throw new CommonException(INVALID_ARGUMENT);
        return new ChannelNotificationAcknowledgement("text/plain;charset=UTF-8", accepted ? "success" : "failure");
    }
}
