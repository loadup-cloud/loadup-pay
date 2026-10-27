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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.wechat.protocol.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Query/close proofs always use the original order number and original merchant identity. */
public final class WechatPaymentAdapter {
    private final WechatProtocolClient client;
    private final WechatResponseReader responses;
    private final JsonMapper json;
    private final Clock clock;
    public WechatPaymentAdapter(WechatProtocolClient client, JsonMapper json, Clock clock) {
        this.client = client; this.responses = new WechatResponseReader(json); this.json = json; this.clock = clock;
    }
    public PaymentObservation query(ChannelExecutionContext context, ChannelPaymentTarget target) {
        return probe(context, target).observation();
    }
    private Probe probe(ChannelExecutionContext context, ChannelPaymentTarget target) {
        var account = original(context, target);
        boolean partner = account.subMerchantId() != null;
        var operation = partner ? WechatMethod.PARTNER_QUERY : WechatMethod.DIRECT_QUERY;
        var params = partner ? Map.of("sp_mchid", List.of(account.merchantId()), "sub_mchid", List.of(account.subMerchantId()))
                : Map.of("mchid", List.of(account.merchantId()));
        var response = client.call(context.tenantId(), context.binding(), operation,
                Map.of("out_trade_no", target.identity().channelRequestNo()), params, null);
        var data = responses.read(response, operation);
        if (response.status() != 200) return new Probe(observation(target, ChannelPaymentOutcome.Kind.UNKNOWN, null, null,
                PaymentObservation.Source.QUERY_RESPONSE, response.sourceDigest()), null);
        responses.account(data, account); responses.match(data, "out_trade_no", target.identity().channelRequestNo());
        responses.amount(data, target.amount().getCent());
        var state = responses.text(data, "trade_state", 32);
        var tradeType = responses.optionalText(data, "trade_type", 16);
        var tradeNo = responses.optionalText(data, "transaction_id", 64);
        if ("SUCCESS".equals(state) || "REFUND".equals(state)) {
            if (tradeNo == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            final LocalDateTime succeededAt;
            try { succeededAt = OffsetDateTime.parse(responses.text(data, "success_time", 64)).atZoneSameInstant(clock.getZone()).toLocalDateTime(); }
            catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
            return new Probe(observation(target, ChannelPaymentOutcome.Kind.SUCCEEDED, tradeNo, succeededAt,
                    PaymentObservation.Source.QUERY_RESPONSE, response.sourceDigest()), tradeType);
        }
        var kind = "NOTPAY".equals(state) || "USERPAYING".equals(state)
                ? ChannelPaymentOutcome.Kind.ACCEPTED : ChannelPaymentOutcome.Kind.UNKNOWN;
        // CLOSED/REVOKED/PAYERROR or NOT_FOUND alone do not prove that money was never collected.
        return new Probe(observation(target, kind, tradeNo, null, PaymentObservation.Source.QUERY_RESPONSE, response.sourceDigest()), tradeType);
    }
    public ChannelPaymentSubmission recover(ChannelExecutionContext context, ChannelPaymentRecovery request) {
        if (request == null) throw new CommonException(INVALID_ARGUMENT);
        var observed = query(context, new ChannelPaymentTarget(request.identity(), request.amount()));
        // Query does not return prepay_id; never create another prepay order during query recovery.
        return new ChannelPaymentSubmission(request.identity(), request.amount(), observed, null);
    }
    public PaymentObservation close(ChannelExecutionContext context, ChannelPaymentTarget target) {
        var account = original(context, target);
        var original = probe(context, target);
        var before = original.observation();
        // Micropay reversal can refund a paid transaction; it requires a separate compensation protocol and mTLS.
        // Neither the V3 unpaid close nor a fabricated no-collection fact is valid for this product.
        if (!"JSAPI".equals(original.tradeType()) && !"MWEB".equals(original.tradeType())) return before;
        // A pending observation can also mean USERPAYING; only an acknowledged close establishes the stop boundary.
        if (before.kind() != ChannelPaymentOutcome.Kind.ACCEPTED) return before;
        boolean partner = account.subMerchantId() != null;
        var operation = partner ? WechatMethod.PARTNER_CLOSE : WechatMethod.DIRECT_CLOSE;
        Map<String, String> body = partner ? Map.of("sp_mchid", account.merchantId(), "sub_mchid", account.subMerchantId())
                : Map.of("mchid", account.merchantId());
        var response = client.call(context.tenantId(), context.binding(), operation,
                Map.of("out_trade_no", target.identity().channelRequestNo()), Map.of(), json.writeValueAsString(body));
        if (response.method() != operation) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        if (response.status() != 204) return query(context, target);
        if (!"".equals(response.payload()) || response.sourceDigest() == null || !response.sourceDigest().matches("[0-9a-f]{64}"))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        var digest = DigestUtils.sha256("wechat-unpaid-close-v1|" + before.sourceDigest() + "|" + response.sourceDigest());
        return observation(target, ChannelPaymentOutcome.Kind.NO_COLLECTION, before.channelTransactionNo(), null,
                PaymentObservation.Source.CLOSE_RESPONSE, digest);
    }
    private record Probe(PaymentObservation observation, String tradeType) {
        @Override public String toString() { return io.github.loadup.commons.json.ToStringAsJson.reflectionToString(this); }
    }
    private WechatAccountIdentity original(ChannelExecutionContext context, ChannelPaymentTarget target) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        var account = WechatBindingGuard.require(context, target.identity());
        if (!target.identity().channelRequestNo().matches("[A-Za-z0-9_-]{1,32}")) throw new CommonException(INVALID_ARGUMENT);
        return account;
    }
    private PaymentObservation observation(ChannelPaymentTarget target, ChannelPaymentOutcome.Kind kind, String tradeNo,
            LocalDateTime succeededAt, PaymentObservation.Source source, String digest) {
        return new PaymentObservation(target.identity(), kind, target.amount(), tradeNo, succeededAt, source,
                source.name().toLowerCase(java.util.Locale.ROOT) + ":" + digest, digest);
    }
}
