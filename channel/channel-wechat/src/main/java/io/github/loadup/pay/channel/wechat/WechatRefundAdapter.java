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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.wechat.protocol.*;
import io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Original-account domestic refund adapter. Only an authoritative refund query produces terminal facts. */
public final class WechatRefundAdapter {
    private final WechatProtocolClient client;
    private final WechatResponseReader responses;
    private final JsonMapper json;
    private final Clock clock;
    private final WechatNotificationProperties notifications;
    public WechatRefundAdapter(WechatProtocolClient client, JsonMapper json, Clock clock, WechatNotificationProperties notifications) {
        this.client = client; this.responses = new WechatResponseReader(json); this.json = json; this.clock = clock;
        this.notifications = notifications;
    }

    /** Pure metadata only; the caller may hold refund locks while checking capabilities. */
    public boolean canRefund(PreparedChannelRefund refund) {
        return refund != null && refund.original().channel() == Channel.WECHAT
                && refund.original().channelRequestNo().matches("[A-Za-z0-9_-]{1,32}")
                && refund.channelRefundNo().matches("[A-Za-z0-9_\\-|*@]{1,64}")
                && refund.originalChannelTransactionNo().matches("[A-Za-z0-9_-]{1,64}");
    }

    public RefundObservation submit(ChannelExecutionContext context, PreparedChannelRefund refund) {
        var account = original(context, refund);
        var body = new TreeMap<String, Object>();
        // A transaction_id uniquely addresses the original payment; out_trade_no is checked in the response.
        body.put("transaction_id", refund.originalChannelTransactionNo());
        body.put("out_refund_no", refund.channelRefundNo());
        if (account.subMerchantId() != null) body.put("sub_mchid", account.subMerchantId());
        body.put("amount", Map.of("refund", refund.amount().getCent(), "total", refund.originalAmount().getCent(), "currency", "CNY"));
        var notifyUrl = notifications.refundNotifyUrls().getOrDefault(context.tenantId(), Map.of()).get(context.binding().id());
        if (notifyUrl != null) body.put("notify_url", notifyUrl.toASCIIString());
        // An absent explicit refund URL preserves the separately configured merchant-platform refund callback.
        var response = client.call(context.tenantId(), context.binding(), WechatMethod.REFUND, Map.of(), Map.of(), json.writeValueAsString(body));
        var data = responses.read(response, WechatMethod.REFUND);
        var kind = ChannelRefundOutcome.Kind.UNKNOWN;
        LocalDateTime acceptedAt = null;
        if (response.status() == 200) {
            requireIdentity(data, refund, account);
            acceptedAt = acceptedAt(data);
            var status = responses.text(data, "status", 32);
            if ("PROCESSING".equals(status) || "SUCCESS".equals(status)) kind = ChannelRefundOutcome.Kind.ACCEPTED;
        }
        // Even a synchronous SUCCESS must converge through the same canonical query timestamp and receipt path.
        return observation(refund, response, kind, null, null, RefundObservation.Source.SUBMIT_RESPONSE, acceptedAt);
    }

    public RefundObservation query(ChannelExecutionContext context, PreparedChannelRefund refund) {
        var account = original(context, refund);
        return interpret(refund, account, lookup(context, refund, account));
    }

    public RefundObservation recover(ChannelExecutionContext context, PreparedChannelRefund refund) {
        var account = original(context, refund);
        var queried = lookup(context, refund, account);
        var data = responses.read(queried, WechatMethod.REFUND_QUERY);
        if (queried.status() != 404 || !"RESOURCE_NOT_EXISTS".equals(data.get("code"))) return interpret(refund, account, queried);
        // RESOURCE_NOT_EXISTS is not a no-refund proof. The provider's original-number idempotency makes this repeat safe.
        var submitted = submit(context, refund);
        var digest = DigestUtils.sha256("wechat-refund-recovery-v1|" + queried.sourceDigest() + "|" + submitted.sourceDigest());
        return new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                submitted.kind(), refund.amount(), null, null, RefundObservation.Source.SUBMIT_RESPONSE, "refund-recovery:" + digest, digest, submitted.acceptedAt());
    }

    private WechatVerifiedResponse lookup(ChannelExecutionContext context, PreparedChannelRefund refund, WechatAccountIdentity account) {
        var query = account.subMerchantId() == null ? Map.<String, List<String>>of()
                : Map.of("sub_mchid", List.of(account.subMerchantId()));
        return client.call(context.tenantId(), context.binding(), WechatMethod.REFUND_QUERY,
                Map.of("out_refund_no", refund.channelRefundNo()), query, null);
    }

    private RefundObservation interpret(PreparedChannelRefund refund, WechatAccountIdentity account, WechatVerifiedResponse response) {
        var data = responses.read(response, WechatMethod.REFUND_QUERY);
        var kind = ChannelRefundOutcome.Kind.UNKNOWN;
        String reference = null;
        LocalDateTime succeededAt = null;
        LocalDateTime acceptedAt = null;
        if (response.status() == 200) {
            requireIdentity(data, refund, account);
            acceptedAt = acceptedAt(data);
            switch (responses.text(data, "status", 32)) {
                case "SUCCESS" -> {
                    reference = responses.text(data, "refund_id", 64);
                    try {
                        succeededAt = OffsetDateTime.parse(responses.text(data, "success_time", 64))
                                .atZoneSameInstant(clock.getZone()).toLocalDateTime();
                    } catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
                    kind = ChannelRefundOutcome.Kind.SUCCEEDED;
                }
                case "CLOSED" -> {
                    if (data.get("success_time") != null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    kind = ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND;
                }
                case "PROCESSING" -> kind = ChannelRefundOutcome.Kind.ACCEPTED;
                // ABNORMAL holds funds in the provider's intermediate account and can later become SUCCESS.
                default -> { }
            }
        }
        if (acceptedAt != null && succeededAt != null && acceptedAt.isAfter(succeededAt)) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        // Not-found, generic errors and ABNORMAL cannot release the reserved refund capacity.
        return observation(refund, response, kind, reference, succeededAt, RefundObservation.Source.QUERY_RESPONSE, acceptedAt);
    }

    private LocalDateTime acceptedAt(Map<?, ?> data) {
        try {
            return OffsetDateTime.parse(responses.text(data, "create_time", 64)).atZoneSameInstant(clock.getZone()).toLocalDateTime();
        } catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
    }

    private WechatAccountIdentity original(ChannelExecutionContext context, PreparedChannelRefund refund) {
        if (refund == null) throw new CommonException(INVALID_ARGUMENT);
        var account = WechatBindingGuard.require(context, refund.original());
        if (!canRefund(refund)) throw new CommonException(UNSUPPORTED_CAPABILITY);
        return account;
    }
    private void requireIdentity(Map<?, ?> data, PreparedChannelRefund refund, WechatAccountIdentity account) {
        responses.match(data, "transaction_id", refund.originalChannelTransactionNo());
        responses.match(data, "out_trade_no", refund.original().channelRequestNo());
        responses.match(data, "out_refund_no", refund.channelRefundNo());
        responses.text(data, "refund_id", 64);
        responses.amount(data, refund.originalAmount().getCent());
        var amount = (Map<?, ?>) data.get("amount");
        responses.minorUnits(amount, "refund", refund.amount().getCent());
        if (data.get("mchid") != null) responses.match(data, "mchid", account.merchantId());
        if (data.get("sp_mchid") != null) responses.match(data, "sp_mchid", account.merchantId());
        if (data.get("sub_mchid") != null) {
            if (account.subMerchantId() == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            responses.match(data, "sub_mchid", account.subMerchantId());
        }
    }
    private RefundObservation observation(PreparedChannelRefund refund, WechatVerifiedResponse response,
            ChannelRefundOutcome.Kind kind, String reference, LocalDateTime succeededAt, RefundObservation.Source source, LocalDateTime acceptedAt) {
        return new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(), refund.originalChannelTransactionNo(),
                kind, refund.amount(), reference, succeededAt, source, response.method().operation() + ":" + response.sourceDigest(), response.sourceDigest(), acceptedAt);
    }
}
