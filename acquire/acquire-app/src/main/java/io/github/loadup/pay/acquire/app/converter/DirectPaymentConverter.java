/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.client.command.DirectPayCommand;
import io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand;
import io.github.loadup.pay.acquire.client.command.PaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.dto.DirectPayDTO;
import io.github.loadup.pay.acquire.client.dto.DirectPayRecoveryDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentRecoveryDTO;
import io.github.loadup.pay.acquire.domain.model.OrderCreationRecoveryState;
import io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface DirectPaymentConverter {
    @Mapping(target = "interactionKind", source = "interaction")
    DirectPayRecoverCommand toRecovery(DirectPayCommand command);

    default PaymentInteractionKind interactionKind(PaymentInteractionDTO value) {
        return switch (value) {
            case NoInteractionDTO ignored -> PaymentInteractionKind.NONE;
            case PaymentCodeInteractionDTO ignored -> PaymentInteractionKind.PAYMENT_CODE;
            case ChannelIdentityInteractionDTO ignored -> PaymentInteractionKind.CHANNEL_IDENTITY;
            case H5SceneInteractionDTO ignored -> PaymentInteractionKind.H5_SCENE;
        };
    }

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "amountMinor", source = "command.firstPaymentAmountMinor")
    PaymentRecoverCommand toPaymentRecovery(DirectPayRecoverCommand command, String orderId);

    @Mapping(target = "merchantOrderNo", source = "command.merchantOrderNo")
    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "creationState", source = "state")
    @Mapping(target = "resultCode", source = "resultCode")
    @Mapping(target = "order", source = "order")
    @Mapping(target = "payment", source = "payment")
    DirectPayRecoveryDTO toRecoveryDTO(
            DirectPayRecoverCommand command,
            String orderId,
            OrderCreationRecoveryState state,
            String resultCode,
            AcquireOrderDTO order,
            PaymentRecoveryDTO payment);

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "amountMinor", source = "command.firstPaymentAmountMinor")
    PaymentStartCommand toStart(DirectPayCommand command, String orderId);

    @Mapping(target = "order", source = "order")
    @Mapping(target = "payment", source = "payment")
    DirectPayDTO toDTO(AcquireOrderDTO order, PaymentRecoveryDTO payment);
}
