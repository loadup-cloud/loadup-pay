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
package io.github.loadup.pay.acquire.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.client.command.*;
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.acquire.app.service.PaymentIntentRecovery;
import io.github.loadup.pay.acquire.client.enums.PaymentRecoveryState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface CashierPaymentConverter {
    PaymentRecoveryState toRecoveryState(PaymentIntentRecovery.State value);

    @Mapping(target = "orderId", source = "command.orderId")
    @Mapping(target = "actionKey", source = "command.actionKey")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "payment", source = "payment")
    CashierPaymentRecoveryDTO toRecovery(CashierPaymentRecoverCommand command, PaymentRecoveryState state, CashierPaymentDTO payment);

    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "method", constant = "H5")
    PaymentChoiceDTO toH5Choice(Channel channel);

    H5SceneInteractionDTO toInteraction(ChannelH5Input value);

    @Mapping(target = "interaction", source = "interaction")
    PaymentStartCommand toStart(CashierPaymentStartCommand command, PaymentInteractionDTO interaction);

    @Mapping(target = "orderId", source = "payment.orderId")
    @Mapping(target = "paymentId", source = "payment.paymentId")
    @Mapping(target = "accepted", constant = "true")
    @Mapping(target = "state", source = "payment.state")
    @Mapping(target = "action", source = "payment.action")
    @Mapping(target = "version", source = "payment.version")
    @Mapping(target = "order", source = "order")
    CashierPaymentDTO toCashier(PaymentStartDTO payment, CashierOrderDTO order);

    @Mapping(target = "orderId", source = "order.orderId")
    @Mapping(target = "paymentId", source = "paymentId")
    @Mapping(target = "accepted", expression = "java(progress.payment() != null)")
    @Mapping(target = "state", source = "progress.payment.state")
    @Mapping(target = "action", source = "progress.payment.action")
    @Mapping(target = "version", expression = "java(progress.payment() == null ? null : Long.valueOf(progress.payment().version()))")
    @Mapping(target = "order", source = "order")
    CashierPaymentDTO toCashier(PaymentProgressDTO progress, String paymentId, CashierOrderDTO order);
}
