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
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentIntentSnapshotConverter;
import io.github.loadup.pay.payment.infrastructure.model.PaymentIntentSnapshot;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PaymentAcceptanceCodecImpl implements PaymentAcceptanceCodec {
    private final JsonMapper mapper;
    private final Validator validator;
    private final PaymentIntentSnapshotConverter snapshots;

    public PaymentAcceptanceCodecImpl(Validator validator, PaymentIntentSnapshotConverter snapshots) {
        this.mapper = JsonMapper.builder().disable(MapperFeature.USE_ANNOTATIONS)
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
        this.validator = validator;
        this.snapshots = snapshots;
    }

    @Override
    public String encode(PaymentExecuteCommand command) {
        try {
            if (command == null || !validator.validate(command).isEmpty()) throw new CommonException(SNAPSHOT_INVALID);
            String payload = mapper.writeValueAsString(snapshots.toSnapshot(command));
            if (payload.getBytes(StandardCharsets.UTF_8).length > 262144) throw new CommonException(SNAPSHOT_INVALID);
            return payload;
        } catch (RuntimeException invalid) {
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
    public PaymentExecuteCommand decode(StoredPaymentAcceptance stored) {
        try {
            if (stored.intentPayload().getBytes(StandardCharsets.UTF_8).length > 262144
                    || !checksum(stored.intentPayload()).equals(stored.payloadHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(stored.intentPayload(), PaymentIntentSnapshot.class);
            var payment = stored.payment();
            if (value == null || !payment.id().equals(value.paymentId()) || !payment.orderId().equals(value.orderId())
                    || !payment.allocationId().equals(value.allocationId()) || !payment.payeeMerchantId().equals(value.payeeMerchantId())
                    || payment.amount().getCent() != value.amountMinor() || !payment.amount().getCurrencyValue().equals(value.currency())
                    || !stored.admissionId().equals(value.admissionId()) || !stored.requestDigest().equals(value.requestDigest()))
                throw new CommonException(SNAPSHOT_INVALID);
            // Storage constructors validate the frozen format. Current public Bean Validation is ingress-only.
            return snapshots.toCommand(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
