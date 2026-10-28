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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_RATE_LIMITED;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * The API's documented merchant/authorized-merchant trade bill grant. Download/profile implementation is separate.
 */
public final class AlipayStatementGrantClient {
    public static final String FORMAT = "alipay-trade-grant-v1";
    private final AlipayProtocolClient client;
    private final AlipayResponseReader reader;
    private final JsonMapper json;
    private final Clock clock;

    public AlipayStatementGrantClient(AlipayProtocolClient client, JsonMapper json, Clock clock) {
        this.client = client;
        this.reader = new AlipayResponseReader(json);
        this.json = json;
        this.clock = clock;
    }

    private static void field(ByteArrayOutputStream output, byte[] bytes) {
        output.writeBytes(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
        if (bytes != null) output.writeBytes(bytes);
    }

    public AlipayStatementGrant request(ChannelExecutionContext context, LocalDate billDate) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (context == null || billDate == null || billDate.getYear() < 1970 || billDate.getYear() > 9998
                || !billDate.isBefore(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDate()))
            throw new CommonException(INVALID_ARGUMENT);
        if (!context.tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var binding = context.binding();
        if (binding.channel() != Channel.ALIPAY || !(binding.identity() instanceof AlipayAccountIdentity))
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        var started = now();
        // trade_zft_merchant/smid and platform-wide ledgers are different products and must never be inferred here.
        var exchange = client.exchangeVerified(context.tenantId(), binding, AlipayMethod.BILL_DOWNLOAD_URL,
                json.writeValueAsString(Map.of("bill_type", "trade", "bill_date", billDate.toString())), Map.of());
        if (exchange == null) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        var payload = reader.read(exchange.response(), AlipayMethod.BILL_DOWNLOAD_URL);
        String code = reader.text(payload, "code");
        if (!"10000".equals(code)) {
            String sub = reader.text(payload, "sub_code");
            if ("40004".equals(code) && ("BILL_NOT_EXIST".equals(sub) || "NO_BILL_DATA".equals(sub)))
                throw new CommonException(STATEMENT_NOT_FOUND);
            if ("SYSTEM_RATE_LIMIT".equals(sub) || "USER_RATE_LIMIT".equals(sub))
                throw new CommonException(STATEMENT_RATE_LIMITED);
            if ("TYPE_NOT_SUPPORTED".equals(sub)) throw new CommonException(UNSUPPORTED_CAPABILITY);
            if ("INVAILID_ARGUMENTS".equals(sub) || "BILL_DATE_BEFORE_REGISTRATION".equals(sub))
                throw new CommonException(INVALID_ARGUMENT);
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        var received = now();
        var expires = started.plusSeconds(30);
        if (!expires.isAfter(received)) throw new CommonException(DEPENDENCY_UNAVAILABLE);
        String url = reader.text(payload, "bill_download_url");
        if (url == null || url.length() > 2048 || url.chars().anyMatch(Character::isISOControl))
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var billFileCode = reader.text(payload, "bill_file_code");
        byte[] proof = evidence(context, billDate, exchange, started, received);
        return new AlipayStatementGrant(binding.id(), binding.accountScopeHash(), billDate, started, received, expires, uri, billFileCode,
                exchange.requestSha256(), DigestUtils.sha256(proof), proof);
    }

    private byte[] evidence(ChannelExecutionContext context, LocalDate date, AlipayVerifiedExchange exchange, OffsetDateTime started, OffsetDateTime received) {
        byte[] envelope = exchange.responseBytes();
        if (envelope.length > 16384) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        var binding = context.binding();
        var identity = (AlipayAccountIdentity) binding.identity();
        var output = new ByteArrayOutputStream();
        for (String value : new String[]{FORMAT, context.tenantId(), binding.id(), binding.accountScopeHash(), date.toString(), "trade", identity.appId(), identity.sellerId(),
                identity.operatorAppId(), binding.credentialReference(), binding.credentialVersion(), identity.authorizationReference(), identity.authorizationVersion(),
                AlipayMethod.BILL_DOWNLOAD_URL.api(), exchange.requestSha256(), started.toInstant().toString(), received.toInstant().toString()})
            field(output, value == null ? null : value.getBytes(StandardCharsets.UTF_8));
        field(output, envelope);
        if (output.size() > 32768) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        return output.toByteArray();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
