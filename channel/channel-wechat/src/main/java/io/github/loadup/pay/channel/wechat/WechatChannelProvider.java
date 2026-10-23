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
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.api.spi.ChannelProvider;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/** Independent WeChat strategy; unsupported capabilities never fall back to another channel. */
public final class WechatChannelProvider implements ChannelProvider {
    private final WechatSubmitAdapter submissions;
    private final WechatPaymentAdapter payments;
    public WechatChannelProvider(WechatSubmitAdapter submissions, WechatPaymentAdapter payments) {
        this.submissions = submissions; this.payments = payments;
    }
    @Override public Channel channel() { return Channel.WECHAT; }
    @Override public void validate(ChannelExecutionContext context, ChannelPaymentValidation request) { submissions.validate(context, request); }
    @Override public ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request) { return submissions.submit(context, request); }
    @Override public PaymentObservation query(ChannelExecutionContext context, ChannelPaymentTarget target) { return payments.query(context, target); }
    @Override public ChannelPaymentSubmission recover(ChannelExecutionContext context, ChannelPaymentRecovery request) { return payments.recover(context, request); }
    @Override public PaymentObservation close(ChannelExecutionContext context, ChannelPaymentTarget target) { return payments.close(context, target); }
    @Override public boolean canRefund(PreparedChannelRefund refund) { return false; }
    @Override public RefundObservation submitRefund(ChannelExecutionContext context, PreparedChannelRefund refund) { throw new CommonException(UNSUPPORTED_CAPABILITY); }
    @Override public RefundObservation queryRefund(ChannelExecutionContext context, PreparedChannelRefund refund) { throw new CommonException(UNSUPPORTED_CAPABILITY); }
}
