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
package io.github.loadup.pay.channel.api.facade;

import io.github.loadup.pay.channel.api.model.*;

/** Account-bound channel operations. Financial results require verified provider evidence. */
public interface ChannelPaymentOperations {
    /** Validate capabilities and parameters without HTTP, KMS, file access or financial side effects. */
    void validate(ChannelExecutionContext context, ChannelPaymentValidation request);

    ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request);

    PaymentObservation query(ChannelExecutionContext context, ChannelPaymentTarget target);

    /** Query the original transaction and reconstruct only provider-supported unexpired actions; never submits. */
    ChannelPaymentSubmission recover(ChannelExecutionContext context, ChannelPaymentRecovery request);

    PaymentObservation close(ChannelExecutionContext context, ChannelPaymentTarget target);

    /** Pure local metadata lookup, safe while the caller holds local locks. */
    boolean canRefund(PreparedChannelRefund refund);

    RefundObservation submitRefund(ChannelExecutionContext context, PreparedChannelRefund refund);

    RefundObservation queryRefund(ChannelExecutionContext context, PreparedChannelRefund refund);

    /** Query first; a provider may repeat the exact original idempotent refund request only under its explicit protocol guarantee. */
    RefundObservation recoverRefund(ChannelExecutionContext context, PreparedChannelRefund refund);
}

