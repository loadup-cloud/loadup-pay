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
package io.github.loadup.pay.payment.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.app.converter.ChannelBindingConverter;
import io.github.loadup.pay.payment.app.port.ChannelBindingCodec;
import io.github.loadup.pay.payment.client.command.ChannelBindingPublishCommand;
import io.github.loadup.pay.payment.client.command.ChannelBindingRevokeCommand;
import io.github.loadup.pay.payment.client.command.ChannelBindingSuspendCommand;
import io.github.loadup.pay.payment.client.dto.ChannelAccountIdentityDTO;
import io.github.loadup.pay.payment.domain.model.AlipayAccountIdentity;
import io.github.loadup.pay.payment.domain.model.ChannelAccountIdentity;
import io.github.loadup.pay.payment.domain.model.WechatAccountIdentity;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class ChannelBindingCodecImpl implements ChannelBindingCodec {
    private final JsonMapper mapper;
    private final ChannelBindingConverter converter;
    private final Validator validator;

    public ChannelBindingCodecImpl(JsonMapper mapper, ChannelBindingConverter converter, Validator validator) {
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.converter = converter;
        this.validator = validator;
    }

    @Override
    public String encodeIdentity(ChannelAccountIdentity identity) {
        String payload = mapper.writerFor(ChannelAccountIdentityDTO.class).writeValueAsString(converter.toIdentityDTO(identity));
        if (payload.length() > 8192) throw new CommonException(SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public ChannelAccountIdentity decodeIdentity(String payload, String expectedHash) {
        try {
            if (payload == null || payload.length() > 8192)
                throw new CommonException(SNAPSHOT_INVALID);
            var identity = mapper.readValue(payload, ChannelAccountIdentityDTO.class);
            if (identity == null || !validator.validate(identity).isEmpty())
                throw new CommonException(SNAPSHOT_INVALID);
            var value = converter.toIdentity(identity);
            // MySQL JSON may reorder keys or add whitespace. Hash the typed canonical representation.
            if (!checksum(encodeIdentity(value)).equals(expectedHash)) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public String checksum(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    @Override
    public String requestDigest(String tenant, String service, String kind, Object command) {
        var fields = new ArrayList<String>(Arrays.asList("payment-binding-change-v1", tenant, service, kind));
        switch (command) {
            case ChannelBindingPublishCommand publish -> {
                fields.addAll(Arrays.asList(publish.payeeMerchantId(), publish.bindingCode(), publish.channel().name(),
                        publish.accessMode().name(), mapper.writerFor(ChannelAccountIdentityDTO.class).writeValueAsString(publish.identity()),
                        publish.credentialReference(), publish.credentialVersion(),
                        Integer.toString(publish.priority()), publish.requestKey(), Long.toString(publish.expectedVersion())));
                publish.capabilities().stream().map(Enum::name).sorted().forEach(fields::add);
            }
            case ChannelBindingSuspendCommand suspend -> fields.addAll(Arrays.asList(suspend.payeeMerchantId(),
                    suspend.bindingCode(), suspend.requestKey(), Long.toString(suspend.expectedVersion())));
            case ChannelBindingRevokeCommand revoke -> fields.addAll(Arrays.asList(revoke.payeeMerchantId(),
                    revoke.bindingCode(), revoke.requestKey(), Long.toString(revoke.expectedVersion())));
            default -> throw new CommonException(SNAPSHOT_INVALID);
        }
        return framedHash(fields);
    }

    @Override
    public String accountScopeHash(Channel channel, ChannelAccountIdentity identity) {
        // Financial uniqueness follows the real collecting account, across tenant/app/credential aliases.
        String receiver = switch (identity) {
            case AlipayAccountIdentity alipay -> {
                if (channel != Channel.ALIPAY) throw new CommonException(SNAPSHOT_INVALID);
                yield alipay.sellerId();
            }
            case WechatAccountIdentity wechat -> {
                if (channel != Channel.WECHAT) throw new CommonException(SNAPSHOT_INVALID);
                yield wechat.subMerchantId() == null ? wechat.merchantId() : wechat.subMerchantId();
            }
        };
        return framedHash(List.of("payment-collecting-account-v1", channel.name(), receiver));
    }

    private String framedHash(List<String> fields) {
        var data = new StringBuilder();
        for (String field : fields) {
            if (field == null) data.append("-1:");
            else data.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return checksum(data.toString());
    }
}
