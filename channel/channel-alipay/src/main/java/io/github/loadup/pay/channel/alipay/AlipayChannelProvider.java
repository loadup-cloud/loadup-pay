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

import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.api.spi.ChannelProvider;


/** Alipay collection and refund strategy, independent of payment application orchestration. */
public final class AlipayChannelProvider implements ChannelProvider {
    private final AlipayRefundAdapter refunds;
    private final AlipayPaymentAdapter payments;
    private final AlipaySubmitAdapter submissions;

    public AlipayChannelProvider(AlipayRefundAdapter refunds, AlipayPaymentAdapter payments, AlipaySubmitAdapter submissions) {
        this.refunds = refunds;
        this.payments = payments;
        this.submissions = submissions;
    }

    @Override
    public Channel channel() { return Channel.ALIPAY; }

    @Override
    public void validate(ChannelExecutionContext context, ChannelPaymentValidation request) {
        submissions.validate(context, request);
    }

    @Override
    public ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request) {
        return submissions.submit(context, request);
    }

    @Override
    public PaymentObservation query(ChannelExecutionContext context, ChannelPaymentTarget target) {
        return payments.query(context, target);
    }

    @Override
    public ChannelPaymentSubmission recover(ChannelExecutionContext context, ChannelPaymentRecovery request) {
        return payments.recover(context, request);
    }

    @Override
    public PaymentObservation close(ChannelExecutionContext context, ChannelPaymentTarget target) {
        return payments.close(context, target);
    }

    @Override
    public boolean canRefund(PreparedChannelRefund refund) {
        return refund != null && refund.original().channel() == Channel.ALIPAY;
    }

    @Override
    public RefundObservation submitRefund(ChannelExecutionContext context, PreparedChannelRefund refund) {
        return refunds.submit(context, refund);
    }

    @Override
    public RefundObservation queryRefund(ChannelExecutionContext context, PreparedChannelRefund refund) {
        return refunds.query(context, refund);
    }
}

