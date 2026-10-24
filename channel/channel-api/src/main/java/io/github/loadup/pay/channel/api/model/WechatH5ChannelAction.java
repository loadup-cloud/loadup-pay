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
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;
import java.time.OffsetDateTime;

/** The untouched, verified provider URL; carries no proof of collection and no merchant return URL. */
public record WechatH5ChannelAction(@DiagnosticHidden String redirectUrl, OffsetDateTime expiresAt) implements ChannelPaymentAction {
    public WechatH5ChannelAction {
        require(expiresAt != null && validUrl(redirectUrl), INVALID_ARGUMENT);
    }

    private static boolean validUrl(String value) {
        if (value == null || value.length() > 4096 || value.chars().anyMatch(Character::isISOControl)) return false;
        try {
            var uri = URI.create(value);
            return "https".equals(uri.getScheme()) && "wx.tenpay.com".equals(uri.getHost())
                    && (uri.getPort() == -1 || uri.getPort() == 443) && uri.getRawUserInfo() == null
                    && uri.getRawFragment() == null && uri.getRawQuery() != null && !uri.getRawQuery().isBlank()
                    && "/cgi-bin/mmpayweb-bin/checkmweb".equals(uri.getRawPath());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    @Override public Channel channel() { return Channel.WECHAT; }
    @Override public PaymentMethod method() { return PaymentMethod.H5; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
