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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.VerifiedChannelNotification;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;

/**
 * Strict RSA2 trade notification verification; the verified notification remains only a query trigger.
 */
public final class AlipayNotificationCodec {
    private final AlipayCredentialReader credentials;

    public AlipayNotificationCodec(AlipayCredentialReader credentials) {
        this.credentials = credentials;
    }

    private static String required(Map<String, String> values, String key, int max) {
        String value = values.get(key);
        if (value == null || value.isBlank() || value.length() > max)
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return value;
    }

    private static void match(Map<String, String> values, String key, String expected) {
        if (!expected.equals(required(values, key, 128))) throw new CommonException(CHANNEL_RESPONSE_INVALID);
    }

    public ChannelNotificationHint identify(ChannelNotificationInput input) {
        return new ChannelNotificationHint(required(parse(input), "out_trade_no", 64));
    }

    public VerifiedChannelNotification verify(ChannelExecutionContext context, ChannelPaymentTarget target,
                                              ChannelNotificationInput input) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        var account = AlipayBindingGuard.require(context, target.identity());
        var parameters = parse(input);
        if (!"RSA2".equals(parameters.get("sign_type")) || !"utf-8".equalsIgnoreCase(parameters.get("charset")))
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        var encodedSignature = required(parameters, "sign", 1024);
        var canonical = new TreeMap<>(parameters);
        canonical.remove("sign");
        canonical.remove("sign_type");
        String signed = AlipayProtocolCodec.canonical(canonical);
        var key = credentials.verificationKey(context.tenantId(), context.binding());
        try {
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(signed.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(encodedSignature)))
                throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        } catch (CommonException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        }
        match(parameters, "out_trade_no", target.identity().channelRequestNo());
        match(parameters, "app_id", account.appId());
        match(parameters, "seller_id", account.sellerId());
        match(parameters, "notify_type", "trade_status_sync");
        var amount = required(parameters, "total_amount", 32);
        if (!amount.matches("(0|[1-9][0-9]{0,15})(\\.[0-9]{1,2})?"))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        try {
            if (new BigDecimal(amount).movePointRight(2).longValueExact() != target.amount().getCent())
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
        } catch (ArithmeticException invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        required(parameters, "notify_time", 32);
        return new VerifiedChannelNotification(target.identity(), target.amount(), required(parameters, "notify_id", 128),
                required(parameters, "trade_no", 128), required(parameters, "trade_status", 64), DigestUtils.sha256(signed));
    }

    private Map<String, String> parse(ChannelNotificationInput input) {
        if (input == null) throw new CommonException(INVALID_ARGUMENT);
        String[] media = input.contentType().split(";", -1);
        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(media[0].strip()))
            throw new CommonException(INVALID_ARGUMENT);
        boolean charset = false;
        for (int i = 1; i < media.length; i++) {
            var parameter = media[i].strip().split("=", 2);
            if (charset || parameter.length != 2 || !"charset".equalsIgnoreCase(parameter[0].strip())
                    || !("utf-8".equalsIgnoreCase(parameter[1].strip()) || "\"utf-8\"".equalsIgnoreCase(parameter[1].strip())))
                throw new CommonException(INVALID_ARGUMENT);
            charset = true;
        }
        byte[] bytes = input.body();
        for (byte value : bytes) if (value < 32 || value > 126) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        String[] fields = new String(bytes, StandardCharsets.US_ASCII).split("&", -1);
        if (fields.length > 256) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        var parameters = new TreeMap<String, String>();
        for (String field : fields) {
            int separator = field.indexOf('=');
            if (separator <= 0) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            String key = decode(field.substring(0, separator)), value = decode(field.substring(separator + 1));
            if (!key.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || value.length() > 16384
                    || parameters.putIfAbsent(key, value) != null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return Map.copyOf(parameters);
    }

    private String decode(String part) {
        try {
            var bytes = new ByteArrayOutputStream(part.length());
            for (int i = 0; i < part.length(); i++) {
                char value = part.charAt(i);
                if (value == '%') {
                    if (i + 2 >= part.length()) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    int high = Character.digit(part.charAt(++i), 16), low = Character.digit(part.charAt(++i), 16);
                    if (high < 0 || low < 0) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    bytes.write((high << 4) | low);
                } else bytes.write(value == '+' ? ' ' : value);
            }
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CommonException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }
}
