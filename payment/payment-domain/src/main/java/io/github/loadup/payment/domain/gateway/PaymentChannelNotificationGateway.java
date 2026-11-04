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
package io.github.loadup.payment.domain.gateway;

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.payment.channel.api.model.ChannelNotificationAcknowledgement;
import io.github.loadup.payment.channel.api.model.ChannelNotificationHint;
import io.github.loadup.payment.channel.api.model.ChannelNotificationInput;
import io.github.loadup.payment.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.payment.channel.api.model.ChannelRefundNotificationHint;
import io.github.loadup.payment.channel.api.model.PreparedChannelRefund;
import io.github.loadup.payment.channel.api.model.VerifiedChannelNotification;
import io.github.loadup.payment.channel.api.model.VerifiedChannelRefundNotification;

public interface PaymentChannelNotificationGateway {
    ChannelNotificationHint identify(Channel channel, String bindingRevisionId, ChannelNotificationInput input);

    VerifiedChannelNotification verify(ChannelPaymentTarget target, ChannelNotificationInput input);

    ChannelRefundNotificationHint identifyRefund(
            Channel channel, String bindingRevisionId, ChannelNotificationInput input);

    VerifiedChannelRefundNotification verifyRefund(PreparedChannelRefund target, ChannelNotificationInput input);

    ChannelNotificationAcknowledgement acknowledgement(Channel channel, boolean accepted);
}
