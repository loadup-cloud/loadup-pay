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

import io.github.loadup.pay.payment.domain.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.PaymentObservation;
import io.github.loadup.pay.payment.domain.model.PreparedChannelPayment;
import io.github.loadup.pay.payment.domain.model.PreparedChannelRefund;
import io.github.loadup.pay.payment.domain.model.RefundObservation;

/**
 * Verified channel integration, invoked outside local payment transactions. Capability lookup is local only.
 */
public interface PaymentChannelGateway {
    PaymentObservation submit(PreparedChannelPayment payment);

    PaymentObservation query(ChannelPaymentIdentity identity);

    PaymentObservation close(ChannelPaymentIdentity identity);

    /**
     * Pure local capability lookup; no network, credential loading or external side effects.
     */
    boolean canRefund(PreparedChannelRefund refund);

    RefundObservation submitRefund(PreparedChannelRefund refund);

    RefundObservation queryRefund(PreparedChannelRefund refund);
}
