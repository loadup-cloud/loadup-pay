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
package io.github.loadup.pay.payment.infrastructure.channel;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelNotificationFacade;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.api.model.ChannelNotificationAcknowledgement;
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.VerifiedChannelNotification;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelNotificationGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CHANNEL_BINDING_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;

@Component
public class PaymentChannelNotificationGatewayImpl implements PaymentChannelNotificationGateway {
    private final ChannelNotificationFacade notifications;
    private final ChannelBindingGateway bindings;

    public PaymentChannelNotificationGatewayImpl(ChannelNotificationFacade notifications, ChannelBindingGateway bindings) {
        this.notifications = notifications;
        this.bindings = bindings;
    }

    @Override
    public ChannelNotificationHint identify(Channel channel, String bindingRevisionId, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (channel == null || bindingRevisionId == null || input == null) throw new CommonException(INVALID_ARGUMENT);
        var tenant = TenantUtil.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new CommonException(ACTION_FORBIDDEN);
        var binding = bindings.findRevision(tenant, bindingRevisionId)
                .orElseThrow(() -> new CommonException(CHANNEL_BINDING_UNAVAILABLE));
        if (binding.channel() != channel) throw new CommonException(INVALID_ARGUMENT);
        return notifications.identify(new ChannelExecutionContext(tenant, binding), input);
    }

    @Override
    public VerifiedChannelNotification verify(ChannelPaymentTarget target, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (target == null || input == null) throw new CommonException(INVALID_ARGUMENT);
        var tenant = TenantUtil.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new CommonException(ACTION_FORBIDDEN);
        var binding = bindings.findRevision(tenant, target.identity().bindingRevisionId())
                .orElseThrow(() -> new CommonException(CHANNEL_BINDING_UNAVAILABLE));
        return notifications.verify(new ChannelExecutionContext(tenant, binding), target, input);
    }

    @Override
    public ChannelRefundNotificationHint identifyRefund(Channel channel, String bindingRevisionId, ChannelNotificationInput input) {
        return notifications.identifyRefund(context(channel, bindingRevisionId), input);
    }

    @Override
    public VerifiedChannelRefundNotification verifyRefund(PreparedChannelRefund target, ChannelNotificationInput input) {
        if (target == null || input == null) throw new CommonException(INVALID_ARGUMENT);
        return notifications.verifyRefund(context(target.original().channel(), target.original().bindingRevisionId()), target, input);
    }

    private ChannelExecutionContext context(Channel channel, String revision) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (channel == null || revision == null) throw new CommonException(INVALID_ARGUMENT);
        var tenant = TenantUtil.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new CommonException(ACTION_FORBIDDEN);
        var binding = bindings.findRevision(tenant, revision).orElseThrow(() -> new CommonException(CHANNEL_BINDING_UNAVAILABLE));
        if (binding.channel() != channel) throw new CommonException(INVALID_ARGUMENT);
        return new ChannelExecutionContext(tenant, binding);
    }

    @Override
    public ChannelNotificationAcknowledgement acknowledgement(Channel channel, boolean accepted) {
        return notifications.acknowledgement(channel, accepted);
    }
}
