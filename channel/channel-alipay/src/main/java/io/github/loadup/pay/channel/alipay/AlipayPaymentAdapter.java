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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.channel.alipay.protocol.AlipayBindingGuard;
import io.github.loadup.pay.channel.alipay.protocol.AlipayMethod;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolClient;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolTime;
import io.github.loadup.pay.channel.alipay.protocol.AlipayResponseReader;
import io.github.loadup.pay.channel.alipay.protocol.AlipayVerifiedResponse;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.pay.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.pay.channel.api.model.AlipayJsapiChannelAction;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Verified collection queries and unpaid-only close; never treats a closed/refunded trade as uncollected. */
public final class AlipayPaymentAdapter {
    private final AlipayProtocolClient client;
    private final JsonMapper json;
    private final AlipayResponseReader responses;
    private final Clock clock;

    public AlipayPaymentAdapter(AlipayProtocolClient client, JsonMapper json, Clock clock) {
        this.client = client;
        this.json = json;
        this.responses = new AlipayResponseReader(json);
        this.clock = clock;
    }

    public PaymentObservation query(ChannelExecutionContext context, ChannelPaymentTarget target) {
        requireTarget(context, target);
        var verified = client.call(context.tenantId(), context.binding(), AlipayMethod.QUERY,
                json.writeValueAsString(Map.of("out_trade_no", target.identity().channelRequestNo())), Map.of());
        var response = responses.read(verified, AlipayMethod.QUERY);
        responses.matchIfPresent(response, "out_trade_no", target.identity().channelRequestNo());
        String transactionNo = null;
        LocalDateTime succeededAt = null;
        var kind = ChannelPaymentOutcome.Kind.UNKNOWN;
        if ("10000".equals(responses.text(response, "code"))) {
            responses.requireMatch(response, "out_trade_no", target.identity().channelRequestNo());
            responses.requireAmount(responses.text(response, "total_amount"), target.amount().getCent());
            responses.matchIfPresent(response, "trans_currency", "CNY");
            transactionNo = responses.requiredText(response, "trade_no", 64);
            String status = responses.requiredText(response, "trade_status", 64);
            if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
                try {
                    succeededAt = AlipayProtocolTime.businessTime(responses.requiredText(response, "send_pay_date", 32), clock.getZone());
                } catch (RuntimeException invalidTime) {
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                }
                kind = ChannelPaymentOutcome.Kind.SUCCEEDED;
            } else if ("WAIT_BUYER_PAY".equals(status)) {
                if (response.get("send_pay_date") != null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                kind = ChannelPaymentOutcome.Kind.ACCEPTED;
            }
            // TRADE_CLOSED also means fully refunded after collection. It cannot prove NO_COLLECTION.
        }
        return new PaymentObservation(target.identity(), kind, target.amount(), transactionNo, succeededAt,
                PaymentObservation.Source.QUERY_RESPONSE, verified.method().operation() + ":" + verified.sourceDigest(),
                verified.sourceDigest());
    }

    public ChannelPaymentSubmission recover(ChannelExecutionContext context, ChannelPaymentRecovery request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var observed = query(context, new ChannelPaymentTarget(request.identity(), request.amount()));
        // Both the initial CREATE and recovery use the original second-truncated deadline, never a new window.
        var expiry = request.payableUntil().withNano(0);
        var action = request.method() == PaymentMethod.JSAPI && observed.kind() == ChannelPaymentOutcome.Kind.ACCEPTED
                && clock.instant().isBefore(expiry.toInstant())
                ? new AlipayJsapiChannelAction(observed.channelTransactionNo(), expiry) : null;
        return new ChannelPaymentSubmission(request.identity(), request.amount(), observed, action);
    }

    public PaymentObservation close(ChannelExecutionContext context, ChannelPaymentTarget target) {
        // Establish an unpaid original transaction before invoking the provider's unpaid-only close.
        var before = query(context, target);
        if (before.kind() != ChannelPaymentOutcome.Kind.ACCEPTED) return before;
        var verified = client.call(context.tenantId(), context.binding(), AlipayMethod.CLOSE,
                json.writeValueAsString(Map.of("out_trade_no", target.identity().channelRequestNo(),
                        "trade_no", before.channelTransactionNo())), Map.of());
        var response = responses.read(verified, AlipayMethod.CLOSE);
        responses.matchIfPresent(response, "out_trade_no", target.identity().channelRequestNo());
        responses.matchIfPresent(response, "trade_no", before.channelTransactionNo());
        if (!"10000".equals(responses.text(response, "code"))) {
            // Payment can win between query and close. Requery the same request instead of interpreting an error as proof.
            return query(context, target);
        }
        responses.requireMatch(response, "out_trade_no", target.identity().channelRequestNo());
        responses.requireMatch(response, "trade_no", before.channelTransactionNo());
        // Both signed responses are needed for the no-collection conclusion; preserve a composite source digest.
        var digest = DigestUtils.sha256("alipay-close|" + before.sourceDigest() + "|" + verified.sourceDigest());
        return new PaymentObservation(target.identity(), ChannelPaymentOutcome.Kind.NO_COLLECTION, target.amount(),
                before.channelTransactionNo(), null, PaymentObservation.Source.CLOSE_RESPONSE, "trade-close:" + digest, digest);
    }

    private void requireTarget(ChannelExecutionContext context, ChannelPaymentTarget target) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        AlipayBindingGuard.require(context, target.identity());
    }
}

