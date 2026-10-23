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
package io.github.loadup.pay.payment.app.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;

/**
 * Returned only to the transaction winner; never reconstruct or expose this through an HTTP response.
 */
public record PaymentSubmissionTicket(String tenantId, String paymentId, String attemptId,
                                      PreparedChannelPayment payment) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
