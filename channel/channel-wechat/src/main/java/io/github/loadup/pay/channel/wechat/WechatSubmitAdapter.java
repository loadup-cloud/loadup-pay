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
package io.github.loadup.pay.channel.wechat;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.channel.api.model.ChannelPayerIdentityInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.PayerIdentityType;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties;
import io.github.loadup.pay.channel.wechat.protocol.WechatBindingGuard;
import io.github.loadup.pay.channel.wechat.protocol.WechatJsapiSigner;
import io.github.loadup.pay.channel.wechat.protocol.WechatMethod;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolClient;
import io.github.loadup.pay.channel.wechat.protocol.WechatResponseReader;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * APIv3 JSAPI and H5 prepay adapter for direct and service-provider merchant accounts.
 */
public final class WechatSubmitAdapter {
    private final WechatProtocolClient client;
    private final WechatJsapiSigner signer;
    private final WechatNotificationProperties notifications;
    private final WechatResponseReader responses;
    private final JsonMapper json;
    private final Clock clock;

    public WechatSubmitAdapter(WechatProtocolClient client, WechatJsapiSigner signer,
                               WechatNotificationProperties notifications, JsonMapper json, Clock clock) {
        this.client = client;
        this.signer = signer;
        this.notifications = notifications;
        this.responses = new WechatResponseReader(json);
        this.json = json;
        this.clock = clock;
    }

    public void validate(ChannelExecutionContext context, ChannelPaymentValidation request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        validate(context, request.identity(), request.amount().getCent(), request.method(), request.subject(), request.payableUntil(), request.input());
    }

    private void validate(ChannelExecutionContext context, ChannelPaymentIdentity identity, long amount,
                          PaymentMethod method, String subject, OffsetDateTime until, ChannelPaymentInput input) {
        var account = WechatBindingGuard.require(context, identity);
        if ((method != PaymentMethod.JSAPI && method != PaymentMethod.H5) || !context.binding().capabilities().contains(method))
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        if (!identity.channelRequestNo().matches("[A-Za-z0-9_-]{1,32}") || amount <= 0
                || subject == null || subject.getBytes(StandardCharsets.UTF_8).length > 127
                || subject.isBlank() || subject.chars().anyMatch(Character::isISOControl) || until == null
                || Duration.between(clock.instant(), until.withNano(0).toInstant()).getSeconds() < 60)
            throw new CommonException(INVALID_ARGUMENT);
        if (method == PaymentMethod.JSAPI) {
            if (!(input instanceof ChannelPayerIdentityInput payer) || payer.identityType() != PayerIdentityType.OPEN_ID
                    || !payer.identifier().matches("[A-Za-z0-9_-]{1,128}")) throw new CommonException(INVALID_ARGUMENT);
            String appId = account.subAppId() == null ? account.appId() : account.subAppId();
            if (payer.channel() != identity.channel() || !appId.equals(payer.appScope()))
                throw new CommonException(CHANNEL_BINDING_MISMATCH);
        } else if (!(input instanceof ChannelH5Input)) throw new CommonException(INVALID_ARGUMENT);
        if (notifications.notifyUrls().getOrDefault(context.tenantId(), Map.of()).get(context.binding().id()) == null)
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
    }

    public ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var payment = request.payment();
        validate(context, payment.identity(), payment.amount().getCent(), payment.method(), request.subject(), request.payableUntil(), request.input());
        var account = (WechatAccountIdentity) context.binding().identity();
        var body = new TreeMap<String, Object>();
        boolean partner = account.subMerchantId() != null;
        body.put(partner ? "sp_appid" : "appid", account.appId());
        body.put(partner ? "sp_mchid" : "mchid", account.merchantId());
        if (partner) body.put("sub_mchid", account.subMerchantId());
        if (account.subAppId() != null) body.put("sub_appid", account.subAppId());
        body.put("description", request.subject());
        body.put("out_trade_no", payment.identity().channelRequestNo());
        body.put("amount", Map.of("total", payment.amount().getCent(), "currency", "CNY"));
        body.put("time_expire", request.payableUntil().withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        body.put("notify_url", notifications.notifyUrls().get(context.tenantId()).get(context.binding().id()).toASCIIString());
        final WechatMethod operation;
        if (payment.method() == PaymentMethod.H5) {
            var scene = (ChannelH5Input) request.input();
            String type = switch (scene.sceneType()) {
                case WAP -> "Wap";
                case IOS -> "iOS";
                case ANDROID -> "Android";
            };
            body.put("scene_info", Map.of("payer_client_ip", scene.payerClientIp(), "h5_info", Map.of("type", type)));
            operation = partner ? WechatMethod.PARTNER_H5 : WechatMethod.DIRECT_H5;
        } else {
            var payer = (ChannelPayerIdentityInput) request.input();
            body.put("payer", Map.of(partner ? (account.subAppId() == null ? "sp_openid" : "sub_openid") : "openid", payer.identifier()));
            operation = partner ? WechatMethod.PARTNER_JSAPI : WechatMethod.DIRECT_JSAPI;
        }
        var startedAt = OffsetDateTime.now(clock).withNano(0);
        var response = client.call(context.tenantId(), context.binding(), operation, Map.of(), Map.of(), json.writeValueAsString(body));
        var data = responses.read(response, operation);
        var kind = response.status() == 200 ? ChannelPaymentOutcome.Kind.ACCEPTED : ChannelPaymentOutcome.Kind.UNKNOWN;
        var observation = new PaymentObservation(payment.identity(), kind, payment.amount(), null, null,
                PaymentObservation.Source.SUBMIT_RESPONSE, operation.operation() + ":" + response.sourceDigest(), response.sourceDigest());
        if (response.status() != 200)
            return new ChannelPaymentSubmission(payment.identity(), payment.amount(), observation, null);
        var expiry = request.payableUntil().withNano(0);
        if (payment.method() == PaymentMethod.H5) {
            if (startedAt.plusMinutes(5).isBefore(expiry)) expiry = startedAt.plusMinutes(5);
            final WechatH5ChannelAction action;
            try {
                action = new WechatH5ChannelAction(responses.text(data, "h5_url", 4096), expiry);
            } catch (RuntimeException invalid) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            return new ChannelPaymentSubmission(payment.identity(), payment.amount(), observation,
                    expiry.toInstant().isAfter(clock.instant()) ? action : null);
        }
        var prepayId = responses.text(data, "prepay_id", 128);
        if (startedAt.plusHours(2).isBefore(expiry)) expiry = startedAt.plusHours(2);
        var action = expiry.toInstant().isAfter(clock.instant()) ? signer.sign(context, payment.identity(), prepayId, expiry) : null;
        return new ChannelPaymentSubmission(payment.identity(), payment.amount(), observation, action);
    }
}
