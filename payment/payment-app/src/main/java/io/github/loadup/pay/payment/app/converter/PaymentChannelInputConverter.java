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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentChannelInputConverter {
    ChannelPaymentCodeInput toCode(PaymentCodeInteractionDTO value);

    ChannelPayerIdentityInput toIdentity(ChannelIdentityInteractionDTO value);

    default ChannelPaymentInput toInput(PaymentInteractionDTO value) {
        return switch (value) {
            case PaymentCodeInteractionDTO code -> toCode(code);
            case ChannelIdentityInteractionDTO payer -> toIdentity(payer);
            case NoInteractionDTO ignored -> new NoChannelInput();
        };
    }

    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "subject", source = "command.subject")
    @Mapping(target = "payableUntil", source = "command.payableUntil")
    @Mapping(target = "input", source = "input")
    ChannelPaymentRequest toRequest(PreparedChannelPayment payment, PaymentExecuteCommand command, ChannelPaymentInput input);

    @Mapping(target = "identity", expression = "java(stored.attempt().identity())")
    @Mapping(target = "amount", source = "stored.attempt.amount")
    @Mapping(target = "method", source = "stored.attempt.method")
    @Mapping(target = "subject", source = "command.subject")
    @Mapping(target = "payableUntil", source = "command.payableUntil")
    @Mapping(target = "input", source = "input")
    ChannelPaymentValidation toValidation(io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt stored,
            PaymentExecuteCommand command, ChannelPaymentInput input);

    default PaymentActionDTO toAction(ChannelPaymentAction value) {
        if (value == null) return null;
        return switch (value) {
            case AlipayJsapiChannelAction alipay -> toAlipayAction(alipay);
            case WechatJsapiChannelAction wechat -> toWechatAction(wechat);
        };
    }

    @Mapping(target = "type", constant = "INVOKE_JSAPI")
    @Mapping(target = "data", expression = "java(toAlipayData(value))")
    PaymentActionDTO toAlipayAction(AlipayJsapiChannelAction value);

    AlipayJsapiActionDTO toAlipayData(AlipayJsapiChannelAction value);

    @Mapping(target = "type", constant = "INVOKE_JSAPI")
    @Mapping(target = "data", expression = "java(toWechatData(value))")
    PaymentActionDTO toWechatAction(WechatJsapiChannelAction value);

    WechatJsapiActionDTO toWechatData(WechatJsapiChannelAction value);
}

