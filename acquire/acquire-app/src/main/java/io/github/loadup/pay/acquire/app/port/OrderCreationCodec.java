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

import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.CheckoutCreateCommand;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;

public interface OrderCreationCodec {
    String encodeIntent(AcquireOrderCreateCommand command);

    AcquireOrderCreateCommand decodeIntent(OrderCreateIntent intent);

    String requestDigest(String tenant, String scopeHash, AcquireOrderCreateCommand command);

    String encodeCheckout(CheckoutCreateCommand command);

    CheckoutCreateCommand decodeCheckout(OrderCreateIntent intent);

    String checkoutDigest(String tenant, String scopeHash, CheckoutCreateCommand command);

    String encodeDirectPay(io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command);

    io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand decodeDirectPay(OrderCreateIntent intent);

    String directPayDigest(String tenant, String scopeHash, io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command);

    String encodeAdmission(OrderAdmissionDTO admission);

    OrderAdmissionDTO decodeAdmission(String payload, String checksum);

    String checksum(String payload);

    String encodeFee(io.github.loadup.pay.acquire.domain.model.OrderFeePolicy policy);
}
