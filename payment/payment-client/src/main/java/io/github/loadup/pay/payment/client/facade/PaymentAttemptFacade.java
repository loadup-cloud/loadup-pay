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
package io.github.loadup.pay.payment.client.facade;

import io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;

/** Persist a fixed channel attempt; this response alone does not establish channel submission or collection. */
public interface PaymentAttemptFacade {
    PaymentExecutionDTO startAttempt(PaymentAttemptStartCommand command);
}
