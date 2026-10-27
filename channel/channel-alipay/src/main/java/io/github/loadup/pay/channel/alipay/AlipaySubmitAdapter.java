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
package io.github.loadup.pay.channel.alipay;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.pay.channel.alipay.config.AlipayH5Properties;
import io.github.loadup.pay.channel.alipay.protocol.*;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.alipay.config.AlipayNotificationProperties;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Payer-code debit, JSAPI creation, or local direct-WAP preparation with independent financial evidence. */
public final class AlipaySubmitAdapter {
    private final AlipayProtocolClient client;
    private final AlipayPaymentAdapter queries;
    private final JsonMapper json;
    private final AlipayResponseReader responses;
    private final Clock clock;
    private final AlipayNotificationProperties notifications;
    private final AlipayH5Properties h5;

    public AlipaySubmitAdapter(AlipayProtocolClient client, AlipayPaymentAdapter queries, JsonMapper json, Clock clock, AlipayNotificationProperties notifications, AlipayH5Properties h5) {
        this.client = client;
        this.queries = queries;
        this.json = json;
        this.responses = new AlipayResponseReader(json);
        this.clock = clock;
        this.notifications = notifications;
        this.h5 = h5;
    }

    public ChannelPaymentSubmission submit(ChannelExecutionContext context, ChannelPaymentRequest request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var payment = request.payment();
        var account = AlipayBindingGuard.require(context, payment.identity());
        validateParameters(context, account, payment.method(), payment.amount().getCent(), request.subject(), request.payableUntil(), request.input());
        var expiry = request.payableUntil().withNano(0);
        var business = new TreeMap<String, Object>();
        business.put("out_trade_no", payment.identity().channelRequestNo());
        business.put("seller_id", account.sellerId());
        business.put("subject", request.subject());
        business.put("total_amount", BigDecimal.valueOf(payment.amount().getCent(), 2).toPlainString());
        business.put("time_expire", AlipayProtocolTime.expiration(expiry));
        if (payment.method() == PaymentMethod.H5) {
            business.remove("seller_id");
            business.put("product_code", "QUICK_WAP_WAY");
            var options = new TreeMap<String, String>();
            options.put("notify_url", notifications.notifyUrls().get(context.tenantId()).get(context.binding().id()).toASCIIString());
            var returnUrl = h5.returnUrls().getOrDefault(context.tenantId(), Map.of()).get(context.binding().id());
            if (returnUrl != null) options.put("return_url", returnUrl.toASCIIString());
            var url = client.directWapUrl(context.tenantId(), context.binding(), h5.gateway(), json.writeValueAsString(business), options);
            return new ChannelPaymentSubmission(payment.identity(), payment.amount(), null, new AlipayH5ChannelAction(url, expiry));
        }
        return payment.method() == PaymentMethod.PAYMENT_CODE
                ? pay(context, request, business) : create(context, request, account, expiry, business);
    }

    public void validate(ChannelExecutionContext context, ChannelPaymentValidation request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var account = AlipayBindingGuard.require(context, request.identity());
        validateParameters(context, account, request.method(), request.amount().getCent(), request.subject(), request.payableUntil(), request.input());
    }

    private void validateParameters(ChannelExecutionContext context, AlipayAccountIdentity account, PaymentMethod method, long amountMinor,
            String subject, OffsetDateTime payableUntil, ChannelPaymentInput input) {
        if ((method != PaymentMethod.PAYMENT_CODE && method != PaymentMethod.JSAPI && method != PaymentMethod.H5)
                || !context.binding().capabilities().contains(method)) throw new CommonException(UNSUPPORTED_CAPABILITY);
        long lifetime = Duration.between(clock.instant(), payableUntil.withNano(0).toInstant()).getSeconds();
        if (lifetime < 60 || lifetime > 15 * 86400L) throw new CommonException(INVALID_ARGUMENT);
        if (subject.chars().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '=' || c == '&'))
            throw new CommonException(INVALID_ARGUMENT);
        if (method == PaymentMethod.H5) {
            if (amountMinor > 10_000_000_000L) throw new CommonException(INVALID_ARGUMENT);
            if (context.binding().accessMode() != AccessMode.DIRECT || account.operatorAppId() != null || h5.gateway() == null)
                throw new CommonException(UNSUPPORTED_CAPABILITY);
            if (!(input instanceof NoChannelInput) && !(input instanceof ChannelH5Input)) throw new CommonException(INVALID_ARGUMENT);
            var notifyUrl = notifications.notifyUrls().getOrDefault(context.tenantId(), Map.of()).get(context.binding().id());
            if (notifyUrl == null || notifyUrl.toASCIIString().length() > 256) throw new CommonException(UNSUPPORTED_CAPABILITY);
        } else if (method == PaymentMethod.PAYMENT_CODE) {
            if (!(input instanceof ChannelPaymentCodeInput code)
                    || !code.paymentCode().matches("(?:2[5-9]|30)[0-9]{14,22}")) throw new CommonException(INVALID_ARGUMENT);
        } else {
            if (!(input instanceof ChannelPayerIdentityInput payer)) throw new CommonException(INVALID_ARGUMENT);
            String scope = account.operatorAppId() == null ? account.appId() : account.operatorAppId();
            if (!scope.equals(payer.appScope()) || payer.channel() != context.binding().channel())
                throw new CommonException(CHANNEL_BINDING_MISMATCH);
            if (payer.identityType() == PayerIdentityType.USER_ID && !payer.identifier().matches("2088[0-9]{12}"))
                throw new CommonException(INVALID_ARGUMENT);
        }
    }

    private ChannelPaymentSubmission pay(ChannelExecutionContext context, ChannelPaymentRequest request, Map<String, Object> business) {
        if (!(request.input() instanceof ChannelPaymentCodeInput code)
                || !code.paymentCode().matches("(?:2[5-9]|30)[0-9]{14,22}")) throw new CommonException(INVALID_ARGUMENT);
        business.put("scene", "bar_code");
        business.put("auth_code", code.paymentCode());
        business.put("trans_currency", "CNY");
        var verified = call(context, AlipayMethod.PAY, business);
        var response = responses.read(verified, AlipayMethod.PAY);
        var payment = request.payment();
        responses.matchIfPresent(response, "out_trade_no", payment.identity().channelRequestNo());
        responses.matchIfPresent(response, "trans_currency", "CNY");
        if (response.get("total_amount") != null)
            responses.requireAmount(responses.text(response, "total_amount"), payment.amount().getCent());
        var codeValue = responses.text(response, "code");
        if ("10000".equals(codeValue)) {
            responses.requireMatch(response, "out_trade_no", payment.identity().channelRequestNo());
            responses.requireAmount(responses.text(response, "total_amount"), payment.amount().getCent());
            var tradeNo = responses.requiredText(response, "trade_no", 64);
            // Canonical success time comes from the same query path used during recovery.
            // Never compare a submit gmt_payment with a different query send_pay_date as duplicate financial facts.
            var observed = queries.query(context, new ChannelPaymentTarget(payment.identity(), payment.amount()));
            if (observed.channelTransactionNo() != null && !tradeNo.equals(observed.channelTransactionNo()))
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            return new ChannelPaymentSubmission(payment.identity(), payment.amount(), observed, null);
        }
        var kind = "10003".equals(codeValue) ? ChannelPaymentOutcome.Kind.ACCEPTED : ChannelPaymentOutcome.Kind.UNKNOWN;
        return new ChannelPaymentSubmission(payment.identity(), payment.amount(), observation(request, verified, kind, null), null);
    }

    private ChannelPaymentSubmission create(ChannelExecutionContext context, ChannelPaymentRequest request,
            AlipayAccountIdentity account, OffsetDateTime expiry, Map<String, Object> business) {
        if (!(request.input() instanceof ChannelPayerIdentityInput payer)) throw new CommonException(INVALID_ARGUMENT);
        String scope = account.operatorAppId() == null ? account.appId() : account.operatorAppId();
        if (!scope.equals(payer.appScope()) || payer.channel() != context.binding().channel())
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        String buyerField;
        if (payer.identityType() == PayerIdentityType.USER_ID) {
            if (!payer.identifier().matches("2088[0-9]{12}")) throw new CommonException(INVALID_ARGUMENT);
            buyerField = "buyer_id";
        } else {
            buyerField = account.operatorAppId() == null ? "buyer_open_id" : "op_buyer_open_id";
        }
        if (account.operatorAppId() != null) business.put("op_app_id", account.operatorAppId());
        business.put(buyerField, payer.identifier());
        var verified = call(context, AlipayMethod.CREATE, business);
        var response = responses.read(verified, AlipayMethod.CREATE);
        responses.matchIfPresent(response, "out_trade_no", request.payment().identity().channelRequestNo());
        responses.matchIfPresent(response, buyerField, payer.identifier());
        if (response.get("total_amount") != null)
            responses.requireAmount(responses.text(response, "total_amount"), request.payment().amount().getCent());
        var payment = request.payment();
        if (!"10000".equals(responses.text(response, "code")))
            return new ChannelPaymentSubmission(payment.identity(), payment.amount(),
                    observation(request, verified, ChannelPaymentOutcome.Kind.UNKNOWN, null), null);
        responses.requireMatch(response, "out_trade_no", payment.identity().channelRequestNo());
        var tradeNo = responses.requiredText(response, "trade_no", 64);
        var accepted = observation(request, verified, ChannelPaymentOutcome.Kind.ACCEPTED, tradeNo);
        return new ChannelPaymentSubmission(payment.identity(), payment.amount(), accepted, new AlipayJsapiChannelAction(tradeNo, expiry));
    }

    private AlipayVerifiedResponse call(ChannelExecutionContext context, AlipayMethod method, Map<String, Object> business) {
        var notifyUrl = notifications.notifyUrls().getOrDefault(context.tenantId(), Map.of()).get(context.binding().id());
        Map<String, String> additional = method == AlipayMethod.CREATE && notifyUrl != null
                ? Map.of("notify_url", notifyUrl.toASCIIString()) : Map.of();
        return client.call(context.tenantId(), context.binding(), method, json.writeValueAsString(business), additional);
    }

    private PaymentObservation observation(ChannelPaymentRequest request, AlipayVerifiedResponse response,
            ChannelPaymentOutcome.Kind kind, String tradeNo) {
        return new PaymentObservation(request.payment().identity(), kind, request.payment().amount(), tradeNo, null,
                PaymentObservation.Source.SUBMIT_RESPONSE, response.method().operation() + ":" + response.sourceDigest(),
                response.sourceDigest());
    }
}

