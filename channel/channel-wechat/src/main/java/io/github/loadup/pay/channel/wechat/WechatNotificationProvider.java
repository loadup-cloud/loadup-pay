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
package io.github.loadup.pay.channel.wechat;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelNotificationAcknowledgement;
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelRefundNotificationHint;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.VerifiedChannelNotification;
import io.github.loadup.pay.channel.api.model.VerifiedChannelRefundNotification;
import io.github.loadup.pay.channel.api.spi.ChannelNotificationProvider;
import io.github.loadup.pay.channel.wechat.protocol.WechatNotificationCodec;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;

/**
 * Native APIv3 acknowledgement. A failure HTTP status must remain non-2xx to request provider retry.
 */
public final class WechatNotificationProvider implements ChannelNotificationProvider {
    private final WechatNotificationCodec codec;

    public WechatNotificationProvider(WechatNotificationCodec codec) {
        this.codec = codec;
    }

    @Override
    public Channel channel() {
        return Channel.WECHAT;
    }

    @Override
    public ChannelNotificationHint identify(ChannelExecutionContext context, ChannelNotificationInput input) {
        if (context == null || context.binding().channel() != channel()) throw new CommonException(INVALID_ARGUMENT);
        return codec.identify(context, input);
    }

    @Override
    public VerifiedChannelNotification verify(ChannelExecutionContext context, ChannelPaymentTarget target, ChannelNotificationInput input) {
        return codec.verify(context, target, input);
    }

    @Override
    public ChannelRefundNotificationHint identifyRefund(ChannelExecutionContext context, ChannelNotificationInput input) {
        if (context == null || context.binding().channel() != channel()) throw new CommonException(INVALID_ARGUMENT);
        return codec.identifyRefund(context, input);
    }

    @Override
    public VerifiedChannelRefundNotification verifyRefund(ChannelExecutionContext context, PreparedChannelRefund target, ChannelNotificationInput input) {
        return codec.verifyRefund(context, target, input);
    }

    @Override
    public ChannelNotificationAcknowledgement acknowledgement(Channel channel, boolean accepted) {
        if (channel != channel()) throw new CommonException(INVALID_ARGUMENT);
        return new ChannelNotificationAcknowledgement(accepted ? 200 : 500, "application/json;charset=UTF-8",
                accepted ? "{\"code\":\"SUCCESS\",\"message\":\"Success\"}" : "{\"code\":\"FAIL\",\"message\":\"Processing failed\"}");
    }
}
