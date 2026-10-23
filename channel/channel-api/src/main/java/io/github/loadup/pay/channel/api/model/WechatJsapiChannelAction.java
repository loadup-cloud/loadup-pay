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
import io.github.loadup.commons.validation.DomainChecks;
import java.time.OffsetDateTime;
import java.util.Objects;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Server-signed invocation data; never evidence that the payer has completed payment. */
public record WechatJsapiChannelAction(String appId, String timeStamp, String nonceStr,
        @DiagnosticHidden String packageValue, String signType, @DiagnosticHidden String paySign,
        OffsetDateTime expiresAt) implements ChannelPaymentAction {
    public WechatJsapiChannelAction {
        DomainChecks.text(appId, 32);
        require(timeStamp != null && timeStamp.matches("[0-9]{1,20}"), INVALID_ARGUMENT);
        require(nonceStr != null && nonceStr.matches("[A-Za-z0-9]{1,32}"), INVALID_ARGUMENT);
        require(packageValue != null && packageValue.matches("prepay_id=[A-Za-z0-9_-]{1,128}"), INVALID_ARGUMENT);
        require("RSA".equals(signType) && paySign != null && paySign.matches("[A-Za-z0-9+/]{342,684}={0,2}"), INVALID_ARGUMENT);
        Objects.requireNonNull(expiresAt);
    }
    @Override public Channel channel() { return Channel.WECHAT; }
    @Override public PaymentMethod method() { return PaymentMethod.JSAPI; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
