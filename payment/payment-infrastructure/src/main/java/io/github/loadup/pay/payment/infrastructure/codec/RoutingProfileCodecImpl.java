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
import io.github.loadup.pay.payment.app.converter.RoutingProfileConverter;
import io.github.loadup.pay.payment.app.port.RoutingProfileCodec;
import io.github.loadup.pay.payment.client.dto.RoutingProfileSpecDTO;
import io.github.loadup.pay.payment.domain.model.RoutingProfileSpec;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class RoutingProfileCodecImpl implements RoutingProfileCodec {
    private final JsonMapper mapper;
    private final RoutingProfileConverter converter;
    private final Validator validator;

    public RoutingProfileCodecImpl(JsonMapper mapper, RoutingProfileConverter converter, Validator validator) {
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.converter = converter;
        this.validator = validator;
    }

    @Override
    public String encode(RoutingProfileSpec spec) {
        String payload = mapper.writerFor(RoutingProfileSpecDTO.class).writeValueAsString(converter.toSpecDTO(spec));
        if (payload.length() > 65536) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return payload;
    }

    @Override
    public RoutingProfileSpec decode(String payload, String expectedHash) {
        try {
            if (payload == null || payload.length() > 65536) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var dto = mapper.readValue(payload, RoutingProfileSpecDTO.class);
            if (dto == null || !validator.validate(dto).isEmpty()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var spec = converter.toSpec(dto);
            // Rule order is material; JSON object field order and whitespace are not.
            if (!checksum(encode(spec)).equals(expectedHash)) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return spec;
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
    public String requestDigest(String tenant, String caller, String key, String contentHash) {
        var framed = new StringBuilder();
        for (String value : List.of("payment-routing-publish-v1", tenant, caller, key, contentHash)) {
            framed.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        }
        return checksum(framed.toString());
    }
}
