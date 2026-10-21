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

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.payment.client.command.ParentPaymentFenceCommand;
import io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.pay.payment.client.command.PaymentCloseCommand;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.ParentFenceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.query.ParentFenceStatusQuery;

/** Business use cases; identity and authorization come from a trusted invocation context. */
public interface PaymentExecutionFacade {
    PaymentExecutionDTO accept(PaymentExecuteCommand command);

    PaymentExecutionDTO detail(IdQuery query);

    PaymentExecutionDTO close(PaymentCloseCommand command);

    ParentFenceDTO fenceParent(ParentPaymentFenceCommand command);

    ParentFenceDTO parentFenceStatus(ParentFenceStatusQuery query);

    PaymentExecutionDTO startAttempt(PaymentAttemptStartCommand command);
}
