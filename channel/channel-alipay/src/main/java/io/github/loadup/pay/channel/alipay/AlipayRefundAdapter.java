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
import io.github.loadup.pay.channel.alipay.protocol.AlipayMethod;
import io.github.loadup.pay.channel.alipay.protocol.AlipayBindingGuard;
import io.github.loadup.pay.channel.alipay.protocol.AlipayResponseReader;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolClient;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolTime;
import io.github.loadup.pay.channel.alipay.protocol.AlipayVerifiedResponse;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Maps verified Alipay refund responses to original-account facts; never infers absence of a refund. */
public final class AlipayRefundAdapter {
    private final AlipayProtocolClient client;
    private final JsonMapper json;
    private final AlipayResponseReader responses;
    private final Clock clock;

    public AlipayRefundAdapter(AlipayProtocolClient client, JsonMapper json, Clock clock) {
        this.client = client;
        this.json = json;
        this.responses = new AlipayResponseReader(json);
        this.clock = clock;
    }

    public RefundObservation submit(ChannelExecutionContext context, PreparedChannelRefund refund) {
        requireContext(context, refund);
        var request = request(refund);
        request.put("refund_amount", BigDecimal.valueOf(refund.amount().getCent(), 2).toPlainString());
        var verified = client.call(context.tenantId(), context.binding(), AlipayMethod.REFUND,
                json.writeValueAsString(request), Map.of());
        var response = responses.read(verified, AlipayMethod.REFUND);
        optionalIdentity(response, refund);
        var kind = ChannelRefundOutcome.Kind.UNKNOWN;
        if ("10000".equals(responses.text(response, "code"))) {
            requireIdentity(response, refund, false);
            // refund_fee may be cumulative and fund_change cannot identify the individual refund.
            // Only the original-request query below can produce a terminal financial observation.
            kind = ChannelRefundOutcome.Kind.ACCEPTED;
        }
        return observation(refund, verified, kind, null, null, RefundObservation.Source.SUBMIT_RESPONSE);
    }

    public RefundObservation query(ChannelExecutionContext context, PreparedChannelRefund refund) {
        requireContext(context, refund);
        return interpret(refund, lookup(context, refund));
    }

    public RefundObservation recover(ChannelExecutionContext context, PreparedChannelRefund refund) {
        requireContext(context, refund);
        var queried = lookup(context, refund);
        var response = responses.read(queried, AlipayMethod.REFUND_QUERY);
        optionalIdentity(response, refund);
        boolean originalIdentity = refund.originalChannelTransactionNo().equals(responses.text(response, "trade_no"))
                && refund.original().channelRequestNo().equals(responses.text(response, "out_trade_no"))
                && refund.channelRefundNo().equals(responses.text(response, "out_request_no"));
        if (!"10000".equals(responses.text(response, "code")) || responses.text(response, "refund_status") != null || !originalIdentity)
            return interpret(refund, queried);
        String amount = responses.text(response, "refund_amount");
        if (amount != null && !java.util.Set.of("0", "0.0", "0.00").contains(amount)) responses.requireAmount(amount, refund.amount().getCent());
        if (response.get("total_amount") != null) responses.requireAmount(responses.text(response, "total_amount"), refund.originalAmount().getCent());
        // Absence of refund_status is not a no-refund proof; stable out_request_no supplies provider idempotency.
        var submitted = submit(context, refund);
        var digest = DigestUtils.sha256("alipay-refund-recovery-v1|" + queried.sourceDigest() + "|" + submitted.sourceDigest());
        return new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                submitted.kind(), refund.amount(), null, null, RefundObservation.Source.SUBMIT_RESPONSE, "refund-recovery:" + digest, digest);
    }

    private AlipayVerifiedResponse lookup(ChannelExecutionContext context, PreparedChannelRefund refund) {
        var request = request(refund);
        request.put("query_options", List.of("gmt_refund_pay"));
        return client.call(context.tenantId(), context.binding(), AlipayMethod.REFUND_QUERY,
                json.writeValueAsString(request), Map.of());
    }

    private RefundObservation interpret(PreparedChannelRefund refund, AlipayVerifiedResponse verified) {
        var response = responses.read(verified, AlipayMethod.REFUND_QUERY);
        optionalIdentity(response, refund);
        var kind = ChannelRefundOutcome.Kind.UNKNOWN;
        String reference = null;
        LocalDateTime succeededAt = null;
        if ("10000".equals(responses.text(response, "code")) && "REFUND_SUCCESS".equals(responses.text(response, "refund_status"))) {
            requireIdentity(response, refund, true);
            responses.requireAmount(responses.text(response, "refund_amount"), refund.amount().getCent());
            if (response.containsKey("total_amount") && response.get("total_amount") != null)
                responses.requireAmount(responses.text(response, "total_amount"), refund.originalAmount().getCent());
            try {
                succeededAt = AlipayProtocolTime.businessTime(responses.text(response, "gmt_refund_pay"), clock.getZone());
            } catch (RuntimeException invalidTime) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            // Alipay's ordinary refund API identifies refunds by this verified natural key, not a refund_id.
            reference = "alipay:" + DigestUtils.sha256(framed(refund.originalChannelTransactionNo(), refund.channelRefundNo()));
            kind = ChannelRefundOutcome.Kind.SUCCEEDED;
        }
        // Missing/unknown status and trade/refund-not-found remain UNKNOWN, including on code 10000.
        return observation(refund, verified, kind, reference, succeededAt, RefundObservation.Source.QUERY_RESPONSE);
    }

    private Map<String, Object> request(PreparedChannelRefund refund) {
        var request = new TreeMap<String, Object>();
        request.put("trade_no", refund.originalChannelTransactionNo());
        request.put("out_trade_no", refund.original().channelRequestNo());
        request.put("out_request_no", refund.channelRefundNo());
        return request;
    }

    private void requireContext(ChannelExecutionContext context, PreparedChannelRefund refund) {
        if (refund == null) throw new CommonException(INVALID_ARGUMENT);
        AlipayBindingGuard.require(context, refund.original());
    }

    private void optionalIdentity(Map<String, Object> response, PreparedChannelRefund refund) {
        responses.matchIfPresent(response, "trade_no", refund.originalChannelTransactionNo());
        responses.matchIfPresent(response, "out_trade_no", refund.original().channelRequestNo());
        responses.matchIfPresent(response, "out_request_no", refund.channelRefundNo());
    }

    private void requireIdentity(Map<String, Object> response, PreparedChannelRefund refund, boolean requireRefundNo) {
        if (!refund.originalChannelTransactionNo().equals(responses.text(response, "trade_no"))
                || !refund.original().channelRequestNo().equals(responses.text(response, "out_trade_no"))
                || (requireRefundNo && !refund.channelRefundNo().equals(responses.text(response, "out_request_no"))))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
    }

    private String framed(String originalTrade, String refundRequest) {
        return originalTrade.length() + ":" + originalTrade + refundRequest.length() + ":" + refundRequest;
    }

    private RefundObservation observation(PreparedChannelRefund refund, AlipayVerifiedResponse response,
            ChannelRefundOutcome.Kind kind, String reference, LocalDateTime succeededAt, RefundObservation.Source source) {
        return new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(),
                refund.originalChannelTransactionNo(), kind, refund.amount(), reference, succeededAt, source,
                response.method().operation() + ":" + response.sourceDigest(), response.sourceDigest());
    }
}

