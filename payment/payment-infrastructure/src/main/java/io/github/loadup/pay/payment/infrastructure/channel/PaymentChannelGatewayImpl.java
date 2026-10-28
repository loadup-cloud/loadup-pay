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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelPaymentFacade;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.service.PaymentChannelRequestFactory;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CHANNEL_BINDING_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;

/**
 * Bridges payment-owned persistence to the independent channel facade; contains no channel protocol.
 */
@Component
public class PaymentChannelGatewayImpl implements PaymentChannelGateway {
    private final ChannelPaymentFacade channels;
    private final ChannelBindingGateway bindings;
    private final PaymentChannelRequestFactory requests;

    public PaymentChannelGatewayImpl(ChannelPaymentFacade channels, ChannelBindingGateway bindings, PaymentChannelRequestFactory requests) {
        this.channels = channels;
        this.bindings = bindings;
        this.requests = requests;
    }

    @Override
    public void validate(ChannelPaymentValidation request) {
        channels.validate(context(request.identity()), request);
    }

    @Override
    public ChannelPaymentSubmission submit(PreparedChannelPayment payment) {
        var context = context(payment.identity());
        return channels.submit(context, requests.create(context.tenantId(), payment));
    }

    @Override
    public PaymentObservation query(ChannelPaymentTarget target) {
        return channels.query(context(target.identity()), target);
    }

    @Override
    public ChannelPaymentSubmission recover(ChannelPaymentRecovery request) {
        return channels.recover(context(request.identity()), request);
    }

    @Override
    public PaymentObservation close(ChannelPaymentTarget target) {
        return channels.close(context(target.identity()), target);
    }

    @Override
    public boolean canRefund(PreparedChannelRefund refund) {
        // This path is called while refund locks are held; never read bindings or credentials here.
        return channels.canRefund(refund);
    }

    @Override
    public RefundObservation submitRefund(PreparedChannelRefund refund) {
        return channels.submitRefund(context(refund.original()), refund);
    }

    @Override
    public RefundObservation recoverRefund(PreparedChannelRefund refund) {
        return channels.recoverRefund(context(refund.original()), refund);
    }

    @Override
    public RefundObservation queryRefund(PreparedChannelRefund refund) {
        return channels.queryRefund(context(refund.original()), refund);
    }

    private ChannelExecutionContext context(ChannelPaymentIdentity identity) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var tenant = TenantUtil.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new CommonException(ACTION_FORBIDDEN);
        var binding = bindings.findRevision(tenant, identity.bindingRevisionId())
                .orElseThrow(() -> new CommonException(CHANNEL_BINDING_UNAVAILABLE));
        return new ChannelExecutionContext(tenant, binding);
    }
}

