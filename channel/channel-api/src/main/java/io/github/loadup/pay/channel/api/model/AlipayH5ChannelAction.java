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
package io.github.loadup.pay.channel.api.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Locally signed direct WAP request; never channel acceptance or collection evidence.
 */
public record AlipayH5ChannelAction(@DiagnosticHidden String redirectUrl, OffsetDateTime expiresAt)
        implements ChannelPaymentAction {
    private static final Set<String> REQUIRED = Set.of("app_id", "method", "format", "charset", "sign_type",
            "sign", "timestamp", "version", "biz_content");

    public AlipayH5ChannelAction {
        require(expiresAt != null && validUrl(redirectUrl), INVALID_ARGUMENT);
    }

    public static boolean isGateway(URI uri) {
        return uri != null && "https".equals(uri.getScheme())
                && uri.getHost() != null && Set.of("openapi.alipay.com", "openapi-sandbox.dl.alipaydev.com").contains(uri.getHost())
                && uri.getPort() == -1 && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                && "/gateway.do".equals(uri.getRawPath());
    }

    private static boolean validUrl(String value) {
        if (value == null || value.length() > 6144 || value.chars().anyMatch(Character::isISOControl)) return false;
        try {
            var uri = URI.create(value);
            if (!isGateway(uri) || uri.getRawQuery() == null) return false;
            var parameters = new HashMap<String, String>();
            for (var part : uri.getRawQuery().split("&", -1)) {
                int separator = part.indexOf('=');
                if (separator <= 0) return false;
                var key = URLDecoder.decode(part.substring(0, separator), StandardCharsets.UTF_8);
                var decoded = URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
                if ((!REQUIRED.contains(key) && !Set.of("notify_url", "return_url").contains(key))
                        || decoded.isBlank() || decoded.chars().anyMatch(Character::isISOControl)
                        || parameters.putIfAbsent(key, decoded) != null) return false;
            }
            return parameters.keySet().containsAll(REQUIRED) && "alipay.trade.wap.pay".equals(parameters.get("method"))
                    && "RSA2".equals(parameters.get("sign_type")) && "utf-8".equals(parameters.get("charset"))
                    && "json".equals(parameters.get("format")) && "1.0".equals(parameters.get("version"));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override
    public Channel channel() {
        return Channel.ALIPAY;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.H5;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
