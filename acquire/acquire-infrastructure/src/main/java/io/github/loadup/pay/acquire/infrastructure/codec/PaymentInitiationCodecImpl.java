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
package io.github.loadup.pay.acquire.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PaymentInitiationCodecImpl implements PaymentInitiationCodec {
    private final JsonMapper mapper;
    private final Validator validator;
    private final OrderCreationCodec hashes;

    public PaymentInitiationCodecImpl(JsonMapper mapper, Validator validator, OrderCreationCodec hashes) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.validator = validator;
        this.hashes = hashes;
    }

    @Override
    public String encode(PaymentExecuteCommand command) {
        String payload = mapper.writeValueAsString(command);
        if (payload.length() > 262144) throw new CommonException(SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public PaymentExecuteCommand decode(StoredPaymentAllocation stored) {
        try {
            if (!hashes.checksum(stored.intentPayload()).equals(stored.payloadHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(stored.intentPayload(), PaymentExecuteCommand.class);
            var allocation = stored.allocation();
            if (value == null
                    || !validator.validate(value).isEmpty()
                    || !allocation.id().equals(value.allocationId())
                    || !allocation.orderId().equals(value.orderId())
                    || !allocation.paymentId().equals(value.paymentId())
                    || !allocation.requestDigest().equals(value.requestDigest())
                    || allocation.amount().getCent() != value.amountMinor()
                    || !allocation.amount().getCurrencyValue().equals(value.currency())
                    || !value.orderId().equals(value.admittedTerms().orderId())
                    || !value.payeeMerchantId().equals(value.admittedTerms().payeeMerchantId())
                    || !value.admissionId().equals(value.admittedTerms().admissionId())
                    || !value.currency().equals(value.admittedTerms().currency())
                    || value.amountMinor() > value.admittedTerms().orderAmountMinor()
                    || value.admittedTerms().terms().schemaVersion() != 1) throw new CommonException(SNAPSHOT_INVALID);
            if (value.preparedInputRef() != null) io.github.loadup.pay.common.DomainChecks.id(value.preparedInputRef());
            if (value.choice().method() == io.github.loadup.pay.common.PaymentMethod.H5
                    && value.preparedInputRef() != null) throw new CommonException(SNAPSHOT_INVALID);
            // A sensitive intent may be staged before secure preparation. It cannot be accepted until attached.
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
