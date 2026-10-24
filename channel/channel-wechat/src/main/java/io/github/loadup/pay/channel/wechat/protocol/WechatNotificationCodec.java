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
package io.github.loadup.pay.channel.wechat.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Authenticated payment notification decoder. Decryption never applies financial state. */
public final class WechatNotificationCodec {
    private final WechatCredentialReader credentials;
    private final WechatProtocolCodec signatures;
    private final WechatResponseReader responses;
    private final JsonMapper json;
    public WechatNotificationCodec(WechatCredentialReader credentials, WechatProtocolCodec signatures, JsonMapper json) {
        this.credentials = credentials; this.signatures = signatures; this.responses = new WechatResponseReader(json);
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    public ChannelNotificationHint identify(ChannelExecutionContext context, ChannelNotificationInput input) {
        var decoded = decode(context, input, false);
        return new ChannelNotificationHint(responses.text(decoded.data(), "out_trade_no", 32));
    }
    public VerifiedChannelNotification verify(ChannelExecutionContext context, ChannelPaymentTarget target, ChannelNotificationInput input) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        WechatBindingGuard.require(context, target.identity());
        var decoded = decode(context, input, false);
        var payment = decoded.data();
        responses.match(payment, "out_trade_no", target.identity().channelRequestNo());
        responses.amount(payment, target.amount().getCent());
        return new VerifiedChannelNotification(target.identity(), target.amount(), decoded.notificationId(),
                responses.text(payment, "transaction_id", 64), "SUCCESS", decoded.digest());
    }
    public ChannelRefundNotificationHint identifyRefund(ChannelExecutionContext context, ChannelNotificationInput input) {
        var decoded = decode(context, input, true);
        return new ChannelRefundNotificationHint(responses.text(decoded.data(), "out_trade_no", 32), responses.text(decoded.data(), "out_refund_no", 64));
    }
    public VerifiedChannelRefundNotification verifyRefund(ChannelExecutionContext context, PreparedChannelRefund target, ChannelNotificationInput input) {
        if (target == null) throw new CommonException(INVALID_ARGUMENT);
        WechatBindingGuard.require(context, target.original());
        var decoded = decode(context, input, true);
        var data = decoded.data();
        responses.match(data, "out_trade_no", target.original().channelRequestNo());
        responses.match(data, "transaction_id", target.originalChannelTransactionNo());
        responses.match(data, "out_refund_no", target.channelRefundNo());
        if (!(data.get("amount") instanceof Map<?, ?> amount)) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        responses.minorUnits(amount, "total", target.originalAmount().getCent());
        responses.minorUnits(amount, "refund", target.amount().getCent());
        if (amount.get("currency") != null) responses.match(amount, "currency", "CNY");
        return new VerifiedChannelRefundNotification(target, decoded.notificationId(), responses.text(data, "refund_id", 64),
                responses.text(data, "refund_status", 32), decoded.digest());
    }
    private Decoded decode(ChannelExecutionContext context, ChannelNotificationInput input, boolean refund) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (context == null || input == null) throw new CommonException(INVALID_ARGUMENT);
        if (!context.tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!(context.binding().identity() instanceof WechatAccountIdentity account)) throw new CommonException(CHANNEL_BINDING_MISMATCH);
        try {
            var type = MediaType.parseMediaType(input.contentType());
            if (!"application".equalsIgnoreCase(type.getType()) || !"json".equalsIgnoreCase(type.getSubtype())
                    || type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset())) throw new CommonException(INVALID_ARGUMENT);
        } catch (RuntimeException invalid) { throw new CommonException(INVALID_ARGUMENT); }
        var headers = new HttpHeaders();
        input.headers().forEach(headers::addAll);
        var signatureType = input.headers().get("wechatpay-signature-type");
        if (signatureType != null && (signatureType.size() != 1 || !"WECHATPAY2-SHA256-RSA2048".equals(signatureType.getFirst())))
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        byte[] body = input.body();
        // Verify the original bytes before parsing or unwrapping an APIv3 secret.
        signatures.verifySignature(credentials.read(context.tenantId(), context.binding()), headers, body);
        var envelope = parse(body);
        String event = responses.text(envelope, "event_type", 32);
        if (refund) {
            if (!java.util.Set.of("REFUND.SUCCESS", "REFUND.CLOSED", "REFUND.ABNORMAL").contains(event))
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
        } else responses.match(envelope, "event_type", "TRANSACTION.SUCCESS");
        responses.match(envelope, "resource_type", "encrypt-resource");
        String notificationId = responses.text(envelope, "id", 128);
        try { OffsetDateTime.parse(responses.text(envelope, "create_time", 64)); }
        catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
        if (!(envelope.get("resource") instanceof Map<?, ?> resource)) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        responses.match(resource, "algorithm", "AEAD_AES_256_GCM");
        responses.match(resource, "original_type", refund ? "refund" : "transaction");
        String associated = "";
        if (resource.get("associated_data") != null) {
            if (!(resource.get("associated_data") instanceof String text) || text.length() > 16
                    || text.chars().anyMatch(Character::isISOControl)) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            associated = text;
        }
        String nonce = responses.text(resource, "nonce", 16);
        byte[] key = null, ciphertext = null, plaintext = null;
        try {
            ciphertext = Base64.getDecoder().decode(responses.text(resource, "ciphertext", ChannelNotificationInput.MAX_BODY_BYTES));
            if (ciphertext.length <= 16 || ciphertext.length > ChannelNotificationInput.MAX_BODY_BYTES)
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            key = credentials.apiV3Key(context.tenantId(), context.binding());
            if (key == null || key.length != 32) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
            plaintext = cipher.doFinal(ciphertext);
            if (plaintext.length == 0 || plaintext.length > ChannelNotificationInput.MAX_BODY_BYTES) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            var payment = parse(plaintext);
            if (refund) {
                if (account.subMerchantId() == null) responses.match(payment, "mchid", account.merchantId());
                else {
                    responses.match(payment, "sp_mchid", account.merchantId());
                    responses.match(payment, "sub_mchid", account.subMerchantId());
                }
                responses.match(payment, "refund_status", event.substring("REFUND.".length()));
                responses.text(payment, "refund_id", 64);
                if (!responses.text(payment, "out_refund_no", 64).matches("[A-Za-z0-9_\\-|*@]{1,64}"))
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                if ("REFUND.CLOSED".equals(event) && payment.get("success_time") != null)
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
            } else {
                responses.account(payment, account);
                responses.match(payment, "trade_state", "SUCCESS");
            }
            responses.text(payment, "transaction_id", 64);
            if (!responses.text(payment, "out_trade_no", 32).matches("[A-Za-z0-9_-]{1,32}")) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            if (!refund || "REFUND.SUCCESS".equals(event)) {
                try { OffsetDateTime.parse(responses.text(payment, "success_time", 64)); }
                catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
            }
            return new Decoded(payment, notificationId, DigestUtils.sha256((refund ? "wechat-refund-notification-v1|" : "wechat-notification-v1|") + utf8(body)));
        } catch (CommonException invalid) { throw invalid; }
        catch (Exception invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
        finally {
            if (key != null) Arrays.fill(key, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }
    private Map<?, ?> parse(byte[] bytes) {
        try {
            var value = json.readValue(utf8(bytes), Map.class);
            if (value == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
            return value;
        } catch (RuntimeException invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
    }
    private String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception invalid) { throw new CommonException(CHANNEL_RESPONSE_INVALID); }
    }
    private record Decoded(@DiagnosticHidden Map<?, ?> data, String notificationId, String digest) {
        @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
    }
}
