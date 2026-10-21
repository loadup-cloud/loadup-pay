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
package io.github.loadup.pay.acquire.client.facade;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.AcquireOrderExtendCommand;
import io.github.loadup.pay.acquire.client.command.CheckoutCreateCommand;
import io.github.loadup.pay.acquire.client.command.DirectPayCommand;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.dto.CheckoutDTO;
import io.github.loadup.pay.acquire.client.dto.DirectPayDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentStartDTO;

/** Business use cases; identity and authorization come from a trusted invocation context. */
public interface AcquireOrderFacade {
    AcquireOrderDTO create(AcquireOrderCreateCommand command);

    CheckoutDTO checkout(CheckoutCreateCommand command);

    DirectPayDTO directPay(DirectPayCommand command);

    PaymentStartDTO startPayment(PaymentStartCommand command);

    AcquireOrderDTO close(AcquireOrderCloseCommand command);

    AcquireOrderDTO extend(AcquireOrderExtendCommand command);

    AcquireOrderDTO detail(IdQuery query);
}
