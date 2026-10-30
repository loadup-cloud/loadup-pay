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
import io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction;
import io.github.loadup.pay.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.channel.api.model.ChannelPayerIdentityInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentCodeInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.NoChannelInput;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.pay.channel.api.model.WechatJsapiChannelAction;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.AlipayJsapiActionDTO;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentActionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.client.dto.RedirectActionDTO;
import io.github.loadup.pay.payment.client.dto.WechatJsapiActionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentChannelInputConverter {
    ChannelPaymentCodeInput toCode(PaymentCodeInteractionDTO value);

    ChannelPayerIdentityInput toIdentity(ChannelIdentityInteractionDTO value);

    ChannelH5Input toH5(H5SceneInteractionDTO value);

    default ChannelPaymentInput toInput(PaymentInteractionDTO value) {
        return switch (value) {
            case PaymentCodeInteractionDTO code -> toCode(code);
            case ChannelIdentityInteractionDTO payer -> toIdentity(payer);
            case H5SceneInteractionDTO h5 -> toH5(h5);
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
        if (value == null) {
            return null;
        }
        return switch (value) {
            case AlipayH5ChannelAction h5 -> toAlipayH5Action(h5);
            case AlipayJsapiChannelAction alipay -> toAlipayAction(alipay);
            case WechatJsapiChannelAction wechat -> toWechatAction(wechat);
            case WechatH5ChannelAction h5 -> toH5Action(h5);
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

    @Mapping(target = "type", constant = "REDIRECT")
    @Mapping(target = "data", expression = "java(toAlipayH5Data(value))")
    PaymentActionDTO toAlipayH5Action(AlipayH5ChannelAction value);

    RedirectActionDTO toAlipayH5Data(AlipayH5ChannelAction value);

    @Mapping(target = "type", constant = "REDIRECT")
    @Mapping(target = "data", expression = "java(toH5Data(value))")
    PaymentActionDTO toH5Action(WechatH5ChannelAction value);

    RedirectActionDTO toH5Data(WechatH5ChannelAction value);
}

