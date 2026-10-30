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
package io.github.loadup.pay.channel.core;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelNotificationFacade;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;

/**
 * Notification strategies are selected independently of payment orchestration and never apply financial state.
 */
public final class DefaultChannelNotificationFacade implements ChannelNotificationFacade {
    private final ChannelStrategyRegistry<ChannelNotificationProvider> providers;

    public DefaultChannelNotificationFacade(Collection<ChannelNotificationProvider> candidates) {
        providers = new ChannelStrategyRegistry<>(candidates, ChannelNotificationProvider::channel);
    }

    @Override
    public ChannelNotificationHint identify(ChannelExecutionContext context, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (context == null || input == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!context.tenantId().equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var result = provider(context.binding().channel()).identify(context, input);
        if (result == null) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return result;
    }

    @Override
    public VerifiedChannelNotification verify(ChannelExecutionContext context, ChannelPaymentTarget target,
                                              ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (context == null || target == null || input == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!context.tenantId().equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var identity = target.identity();
        if (context.binding().channel() != identity.channel() || !context.binding().id().equals(identity.bindingRevisionId())
                || !context.binding().accountScopeHash().equals(identity.accountScopeHash())) {
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        }
        var result = provider(identity.channel()).verify(context, target, input);
        if (result == null || !identity.equals(result.identity()) || !target.amount().equals(result.amount())) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return result;
    }

    @Override
    public ChannelRefundNotificationHint identifyRefund(ChannelExecutionContext context, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (context == null || input == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!context.tenantId().equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var result = provider(context.binding().channel()).identifyRefund(context, input);
        if (result == null) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return result;
    }

    @Override
    public VerifiedChannelRefundNotification verifyRefund(ChannelExecutionContext context, PreparedChannelRefund target, ChannelNotificationInput input) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (context == null || target == null || input == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!context.tenantId().equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var identity = target.original();
        if (context.binding().channel() != identity.channel() || !context.binding().id().equals(identity.bindingRevisionId())
                || !context.binding().accountScopeHash().equals(identity.accountScopeHash())) {
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        }
        var result = provider(identity.channel()).verifyRefund(context, target, input);
        if (result == null || !target.equals(result.refund())) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return result;
    }

    @Override
    public ChannelNotificationAcknowledgement acknowledgement(Channel channel, boolean accepted) {
        var result = provider(channel).acknowledgement(channel, accepted);
        if (result == null) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return result;
    }

    private ChannelNotificationProvider provider(Channel channel) {
        return providers.require(channel);
    }
}
