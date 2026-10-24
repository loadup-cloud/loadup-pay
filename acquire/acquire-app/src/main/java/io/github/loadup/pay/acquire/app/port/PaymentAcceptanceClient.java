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
package io.github.loadup.pay.acquire.app.port;

import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;

/**
 * Service identity is supplied by the transport, never by the merchant.
 */
public interface PaymentAcceptanceClient {
    boolean supportsTenant(String tenant);

    PaymentInputDTO prepareInput(String tenant, PaymentInputPrepareCommand command);

    PaymentInputLookupDTO lookupInput(String tenant, PaymentInputLookupQuery query);

    PaymentExecutionDTO accept(String tenant, PaymentExecuteCommand command);

    PaymentExecutionDTO detail(String tenant, String paymentId);
}
