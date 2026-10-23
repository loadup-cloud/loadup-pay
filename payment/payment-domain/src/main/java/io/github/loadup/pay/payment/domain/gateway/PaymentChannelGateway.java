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
package io.github.loadup.pay.payment.domain.gateway;

import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;

/**
 * Verified channel integration, invoked outside local payment transactions. Capability lookup is local only.
 */
public interface PaymentChannelGateway {
    /** Pre-Y2 validation only; never submits a channel request. */
    void validate(ChannelPaymentValidation request);

    ChannelPaymentSubmission submit(PreparedChannelPayment payment);

    PaymentObservation query(ChannelPaymentTarget target);

    /** Read-only channel recovery; an original attempt snapshot never grants another submission. */
    ChannelPaymentSubmission recover(ChannelPaymentRecovery request);

    PaymentObservation close(ChannelPaymentTarget target);

    /**
     * Pure local capability lookup; no network, credential loading or external side effects.
     */
    boolean canRefund(PreparedChannelRefund refund);

    RefundObservation submitRefund(PreparedChannelRefund refund);

    RefundObservation queryRefund(PreparedChannelRefund refund);
}
