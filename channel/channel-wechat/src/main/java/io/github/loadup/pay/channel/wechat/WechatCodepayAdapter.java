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
import io.github.loadup.pay.channel.api.model.ChannelPaymentCodeInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.wechat.config.WechatCodepayProperties;
import io.github.loadup.pay.channel.wechat.protocol.WechatBindingGuard;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2Client;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2Method;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * Original V2 micropay debit; recovery queries the original request without reusing the payer code.
 */
public final class WechatCodepayAdapter {
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");
    private static final ZoneId PROTOCOL_ZONE = ZoneId.of("Asia/Shanghai");
    private final WechatV2Client client;
    private final WechatPaymentAdapter queries;
    private final WechatCodepayProperties properties;
    private final Clock clock;

    public WechatCodepayAdapter(WechatV2Client client, WechatPaymentAdapter queries, WechatCodepayProperties properties, Clock clock) {
        this.client = client;
        this.queries = queries;
        this.properties = properties;
        this.clock = clock;
    }

    public void validate(ChannelExecutionContext context, ChannelPaymentValidation request) {
        if (request == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        WechatBindingGuard.require(context, request.identity());
        if (request.method() != PaymentMethod.PAYMENT_CODE || !context.binding().capabilities().contains(PaymentMethod.PAYMENT_CODE)) {
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        }
        if (!request.identity().channelRequestNo().matches("[A-Za-z0-9_-]{1,32}")
                || request.amount().getCent() > Integer.MAX_VALUE
                || request.subject().getBytes(StandardCharsets.UTF_8).length > 127
                || request.subject().codePoints().anyMatch(c -> Character.isISOControl(c) || c < 32 || c > 0xFFFF || Character.isSurrogate((char) c))
                || !(request.input() instanceof ChannelPaymentCodeInput code) || !code.paymentCode().matches("1[0-5][0-9]{16}")
                || Duration.between(clock.instant(), request.payableUntil().withNano(0).toInstant()).getSeconds() < 60) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (serverIp(context) == null) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
    }

    public ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request) {
        if (request == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var original = request.payment();
        validate(context, new ChannelPaymentValidation(original.identity(), original.amount(), original.method(),
                request.subject(), request.payableUntil(), request.input()));
        var account = (WechatAccountIdentity) context.binding().identity();
        var fields = new TreeMap<String, String>();
        fields.put("appid", account.appId());
        fields.put("mch_id", account.merchantId());
        if (account.subMerchantId() != null) {
            fields.put("sub_mch_id", account.subMerchantId());
        }
        if (account.subAppId() != null) {
            fields.put("sub_appid", account.subAppId());
        }
        fields.put("out_trade_no", original.identity().channelRequestNo());
        fields.put("body", request.subject());
        fields.put("total_fee", Long.toString(original.amount().getCent()));
        fields.put("fee_type", "CNY");
        fields.put("spbill_create_ip", serverIp(context));
        fields.put("time_expire", request.payableUntil().withNano(0).atZoneSameInstant(PROTOCOL_ZONE).format(EXPIRY));
        fields.put("auth_code", ((ChannelPaymentCodeInput) request.input()).paymentCode());
        fields.put("nonce_str", UUID.randomUUID().toString().replace("-", ""));
        fields.put("sign_type", "HMAC-SHA256");
        var verified = client.call(context.tenantId(), context.binding(), WechatV2Method.MICROPAY, fields);
        if (verified == null || verified.method() != WechatV2Method.MICROPAY) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var response = verified.fields();
        match(response, "return_code", "SUCCESS");
        match(response, "appid", account.appId());
        match(response, "mch_id", account.merchantId());
        if (!Objects.equals(account.subMerchantId(), response.get("sub_mch_id"))
                || !Objects.equals(account.subAppId(), response.get("sub_appid"))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        if (response.containsKey("out_trade_no")) {
            match(response, "out_trade_no", original.identity().channelRequestNo());
        }
        if (response.containsKey("total_fee")) {
            match(response, "total_fee", Long.toString(original.amount().getCent()));
        }
        if (response.containsKey("fee_type")) {
            match(response, "fee_type", "CNY");
        }
        if ("SUCCESS".equals(response.get("result_code"))) {
            match(response, "trade_type", "MICROPAY");
            match(response, "out_trade_no", original.identity().channelRequestNo());
            match(response, "total_fee", Long.toString(original.amount().getCent()));
            var tradeNo = response.get("transaction_id");
            if (tradeNo == null || !tradeNo.matches("[0-9]{1,64}")) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            // The shared V3 query provides a stable success_time for submit, callback and recovery facts.
            var observed = queries.query(context, new ChannelPaymentTarget(original.identity(), original.amount()));
            if (observed.channelTransactionNo() != null && !tradeNo.equals(observed.channelTransactionNo())) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            return new ChannelPaymentSubmission(original.identity(), original.amount(), observed, null);
        }
        if (!"FAIL".equals(response.get("result_code"))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var kind = "USERPAYING".equals(response.get("err_code")) ? ChannelPaymentOutcome.Kind.ACCEPTED : ChannelPaymentOutcome.Kind.UNKNOWN;
        var observed = new PaymentObservation(original.identity(), kind, original.amount(), null, null,
                PaymentObservation.Source.SUBMIT_RESPONSE, "wechat-v2-micropay:" + verified.sourceDigest(), verified.sourceDigest());
        return new ChannelPaymentSubmission(original.identity(), original.amount(), observed, null);
    }

    private String serverIp(ChannelExecutionContext context) {
        return properties.serverIps().getOrDefault(context.tenantId(), Map.of()).get(context.binding().id());
    }

    private void match(Map<String, String> fields, String name, String value) {
        if (!value.equals(fields.get(name))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }
}
