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
import io.github.loadup.pay.channel.api.facade.ChannelPaymentFacade;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.api.spi.ChannelProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Immutable strategy registry; routing does not contain provider-specific branches. */
public final class DefaultChannelPaymentFacade implements ChannelPaymentFacade {
    private final Map<Channel, ChannelProvider> providers;

    public DefaultChannelPaymentFacade(Collection<ChannelProvider> candidates) {
        if (candidates == null) throw new CommonException(INVALID_ARGUMENT);
        var registered = new EnumMap<Channel, ChannelProvider>(Channel.class);
        for (var candidate : candidates) {
            if (candidate == null || candidate.channel() == null) throw new CommonException(INVALID_ARGUMENT);
            if (registered.putIfAbsent(candidate.channel(), candidate) != null)
                throw new CommonException(DUPLICATE_PROVIDER);
        }
        providers = Map.copyOf(registered);
    }

    @Override
    public void validate(ChannelExecutionContext context, ChannelPaymentValidation request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var provider = route(context, request.identity());
        if (!context.binding().capabilities().contains(request.method()))
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        provider.validate(context, request);
    }

    @Override
    public ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var payment = request.payment();
        var provider = route(context, payment.identity());
        if (!context.binding().capabilities().contains(payment.method()))
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        var result = provider.submit(context, request);
        if (result == null || !payment.identity().equals(result.identity()) || !payment.amount().equals(result.amount()))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        if (result.action() != null && (result.action().channel() != payment.identity().channel()
                || result.action().method() != payment.method()
                || result.action().expiresAt().isAfter(request.payableUntil())))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return result;
    }

    @Override
    public PaymentObservation query(ChannelExecutionContext context, ChannelPaymentTarget target) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        return checked(target, route(context, target.identity()).query(context, target));
    }

    @Override
    public ChannelPaymentSubmission recover(ChannelExecutionContext context, ChannelPaymentRecovery request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var result = route(context, request.identity()).recover(context, request);
        if (result == null || !request.identity().equals(result.identity()) || !request.amount().equals(result.amount())
                || result.observation() == null || result.observation().source() != PaymentObservation.Source.QUERY_RESPONSE)
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        if (result.action() != null && (result.action().channel() != request.identity().channel()
                || result.action().method() != request.method() || result.action().expiresAt().isAfter(request.payableUntil())))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return result;
    }

    @Override
    public PaymentObservation close(ChannelExecutionContext context, ChannelPaymentTarget target) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        return checked(target, route(context, target.identity()).close(context, target));
    }

    @Override
    public boolean canRefund(PreparedChannelRefund refund) {
        if (refund == null) throw new CommonException(INVALID_ARGUMENT);
        var provider = providers.get(refund.original().channel());
        return provider != null && provider.canRefund(refund);
    }

    @Override
    public RefundObservation submitRefund(ChannelExecutionContext context, PreparedChannelRefund refund) {
        if (refund == null) throw new CommonException(INVALID_ARGUMENT);
        var provider = route(context, refund.original());
        if (!provider.canRefund(refund)) throw new CommonException(UNSUPPORTED_CAPABILITY);
        return checked(refund, provider.submitRefund(context, refund));
    }

    @Override
    public RefundObservation queryRefund(ChannelExecutionContext context, PreparedChannelRefund refund) {
        if (refund == null) throw new CommonException(INVALID_ARGUMENT);
        var provider = route(context, refund.original());
        if (!provider.canRefund(refund)) throw new CommonException(UNSUPPORTED_CAPABILITY);
        return checked(refund, provider.queryRefund(context, refund));
    }

    private ChannelProvider route(ChannelExecutionContext context, ChannelPaymentIdentity identity) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (context == null || identity == null) throw new CommonException(INVALID_ARGUMENT);
        if (!context.tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var binding = context.binding();
        if (binding.channel() != identity.channel() || !binding.id().equals(identity.bindingRevisionId())
                || !binding.accountScopeHash().equals(identity.accountScopeHash()))
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        var provider = providers.get(identity.channel());
        if (provider == null) throw new CommonException(UNSUPPORTED_CAPABILITY);
        return provider;
    }

    private PaymentObservation checked(ChannelPaymentIdentity identity, PaymentObservation result) {
        if (result == null || !identity.equals(result.identity())) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return result;
    }

    private PaymentObservation checked(ChannelPaymentTarget target, PaymentObservation result) {
        checked(target.identity(), result);
        if (!target.amount().equals(result.amount())) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return result;
    }

    private RefundObservation checked(PreparedChannelRefund refund, RefundObservation result) {
        if (result == null || !refund.refundId().equals(result.refundId()) || !refund.original().equals(result.original())
                || !refund.channelRefundNo().equals(result.channelRefundNo())
                || !refund.originalChannelTransactionNo().equals(result.originalChannelTransactionNo())
                || !refund.amount().equals(result.amount())) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return result;
    }
}

