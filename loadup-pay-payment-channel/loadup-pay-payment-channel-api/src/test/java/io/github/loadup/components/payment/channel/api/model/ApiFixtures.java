/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.api.model;

import io.github.loadup.commons.money.Money;
import io.github.loadup.components.payment.channel.contract.AccessMode;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;

final class ApiFixtures {
    static final String HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String SHORT_HASH = "a".repeat(64);
    static final Money AMOUNT = Money.ofMinor(1250, "CNY");
    static final Money REFUND_AMOUNT = Money.ofMinor(500, "CNY");
    static final OffsetDateTime EXPIRY = OffsetDateTime.parse("2026-10-23T19:00:00.123456789+08:00");
    static final LocalDateTime SUCCEEDED_AT = LocalDateTime.parse("2026-10-23T10:11:12.123456789");
    static final ChannelPaymentIdentity IDENTITY =
            new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.ALIPAY, "revision-1", HASH, "request-1");
    static final ChannelPaymentIdentity WECHAT_IDENTITY =
            new ChannelPaymentIdentity("payment-2", "attempt-2", Channel.WECHAT, "revision-2", HASH, "request-2");
    static final AlipayAccountIdentity ALIPAY_DIRECT = new AlipayAccountIdentity("app", "seller", null, null, null);
    static final AlipayAccountIdentity ALIPAY_PROVIDER =
            new AlipayAccountIdentity("app", "seller", "operator", "auth-ref", "auth-v1");
    static final WechatAccountIdentity WECHAT_DIRECT = new WechatAccountIdentity("wx-app", "merchant", null, null);
    static final WechatAccountIdentity WECHAT_PROVIDER =
            new WechatAccountIdentity("wx-app", "merchant", "sub-app", "sub-merchant");

    private ApiFixtures() {}

    static ChannelBindingRevision alipayBinding() {
        return new ChannelBindingRevision(
                "revision-1",
                "merchant-1",
                Channel.ALIPAY,
                AccessMode.DIRECT,
                HASH,
                "cred-ref",
                "cred-v1",
                ALIPAY_DIRECT,
                Set.of(PaymentMethod.JSAPI, PaymentMethod.H5));
    }

    static ChannelBindingRevision wechatBinding() {
        return new ChannelBindingRevision(
                "revision-1",
                "merchant-1",
                Channel.WECHAT,
                AccessMode.DIRECT,
                HASH,
                "cred-ref",
                "cred-v1",
                WECHAT_DIRECT,
                Set.of(PaymentMethod.JSAPI));
    }

    static ChannelExecutionContext context() {
        return new ChannelExecutionContext(alipayBinding());
    }

    static ChannelExecutionContext wechatContext() {
        return new ChannelExecutionContext(wechatBinding());
    }

    static PreparedChannelPayment prepared(PaymentMethod method) {
        return new PreparedChannelPayment(IDENTITY, AMOUNT, method, 7, "input-ref");
    }

    static PreparedChannelPayment prepared(PaymentMethod method, ChannelPaymentIdentity identity) {
        return new PreparedChannelPayment(identity, AMOUNT, method, 7, "input-ref");
    }

    static PreparedChannelRefund refund() {
        return new PreparedChannelRefund(IDENTITY, "refund-1", "refund-no", REFUND_AMOUNT, AMOUNT, "trade-1");
    }

    static PaymentObservation acceptedObservation() {
        return new PaymentObservation(
                IDENTITY,
                ChannelPaymentOutcome.Kind.ACCEPTED,
                AMOUNT,
                null,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                HASH);
    }

    static PaymentObservation acceptedObservation(ChannelPaymentIdentity identity, String transactionNo, Money amount) {
        return new PaymentObservation(
                identity,
                ChannelPaymentOutcome.Kind.ACCEPTED,
                amount,
                transactionNo,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                HASH);
    }

    static PaymentObservation succeededObservation() {
        return new PaymentObservation(
                IDENTITY,
                ChannelPaymentOutcome.Kind.SUCCEEDED,
                AMOUNT,
                "trade-1",
                SUCCEEDED_AT,
                PaymentObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                HASH);
    }

    static RefundObservation acceptedRefundObservation() {
        return new RefundObservation(
                "refund-1",
                IDENTITY,
                "refund-no",
                "trade-1",
                ChannelRefundOutcome.Kind.ACCEPTED,
                REFUND_AMOUNT,
                null,
                null,
                RefundObservation.Source.QUERY_RESPONSE,
                "receipt-1",
                HASH,
                SUCCEEDED_AT);
    }

    static ChannelStatementScope statementScope() {
        return new ChannelStatementScope(Channel.ALIPAY, HASH, LocalDate.of(2026, 10, 23), "profile");
    }

    static ChannelStatementReceipt statementReceipt() {
        return new ChannelStatementReceipt(
                "csv", HASH, HASH, "SHA1", "a", HASH, EXPIRY, EXPIRY.plusMinutes(1), new byte[] {1});
    }

    static String alipayH5Url() {
        return "https://openapi.alipay.com/gateway.do?app_id=app&method=alipay.trade.wap.pay&format=json"
                + "&charset=utf-8&sign_type=RSA2&sign=AQ%3D%3D&timestamp=2026-10-23+18%3A00%3A00"
                + "&version=1.0&biz_content=%7B%7D";
    }

    static String wechatH5Url() {
        return "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=wx123";
    }
}
