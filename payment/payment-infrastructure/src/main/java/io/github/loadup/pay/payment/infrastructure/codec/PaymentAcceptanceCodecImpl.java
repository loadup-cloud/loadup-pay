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
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PaymentAcceptanceCodecImpl implements PaymentAcceptanceCodec {
    private final JsonMapper mapper;
    private final Validator validator;

    public PaymentAcceptanceCodecImpl(JsonMapper mapper, Validator validator) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.validator = validator;
    }

    @Override
    public String encode(PaymentExecuteCommand command) {
        String payload = mapper.writeValueAsString(command);
        if (payload.length() > 262144) throw new CommonException(SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public String checksum(String payload) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    @Override
    public PaymentExecuteCommand decode(StoredPaymentAcceptance stored) {
        try {
            if (!checksum(stored.intentPayload()).equals(stored.payloadHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(stored.intentPayload(), PaymentExecuteCommand.class);
            var payment = stored.payment();
            if (value == null
                    || !validator.validate(value).isEmpty()
                    || !payment.id().equals(value.paymentId())
                    || !payment.orderId().equals(value.orderId())
                    || !payment.allocationId().equals(value.allocationId())
                    || !payment.payeeMerchantId().equals(value.payeeMerchantId())
                    || payment.amount().getCent() != value.amountMinor()
                    || !payment.amount().getCurrencyValue().equals(value.currency())
                    || !stored.admissionId().equals(value.admissionId())
                    || !stored.requestDigest().equals(value.requestDigest())
                    || !value.orderId().equals(value.admittedTerms().orderId())
                    || !value.admissionId().equals(value.admittedTerms().admissionId())
                    || !value.payeeMerchantId().equals(value.admittedTerms().payeeMerchantId())
                    || !value.currency().equals(value.admittedTerms().currency())
                    || value.amountMinor() > value.admittedTerms().orderAmountMinor()
                    || value.admittedTerms().terms().schemaVersion() != 1) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
