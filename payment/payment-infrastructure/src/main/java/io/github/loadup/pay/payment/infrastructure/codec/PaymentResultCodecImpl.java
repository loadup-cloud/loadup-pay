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

import io.github.loadup.pay.payment.app.port.PaymentResultCodec;
import io.github.loadup.pay.payment.domain.model.PaymentObservation;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

@Component
public class PaymentResultCodecImpl implements PaymentResultCodec {
    private final JsonMapper mapper;

    public PaymentResultCodecImpl(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String encode(PaymentObservation observation) {
        return mapper.writeValueAsString(observation);
    }

    @Override
    public String factsDigest(PaymentObservation value) {
        var identity = value.identity();
        var data = new StringBuilder();
        for (String field : Arrays.asList("payment-result-facts-v1", identity.paymentId(), identity.attemptId(),
                identity.channel().name(), identity.bindingRevisionId(), identity.accountScopeHash(), identity.channelRequestNo(),
                value.kind().name(), Long.toString(value.amount().getCent()), value.amount().getCurrencyValue(),
                value.channelTransactionNo(), value.succeededAt() == null ? null : value.succeededAt().toString())) {
            if (field == null) data.append("-1:");
            else data.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
