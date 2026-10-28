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
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.CheckoutCreateCommand;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class OrderCreationCodecImpl implements OrderCreationCodec {
    private final JsonMapper mapper;
    private final Validator validator;

    public OrderCreationCodecImpl(JsonMapper mapper, Validator validator) {
        this.mapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.validator = validator;
    }

    @Override
    public String encodeIntent(AcquireOrderCreateCommand command) {
        return mapper.writeValueAsString(command);
    }

    @Override
    public AcquireOrderCreateCommand decodeIntent(OrderCreateIntent intent) {
        try {
            if (!"ORDER".equals(intent.entryMode())) throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(intent.intentPayload(), AcquireOrderCreateCommand.class);
            if (value == null
                    || !validator.validate(value).isEmpty()
                    || !intent.payeeMerchantId().equals(value.payeeMerchantId())
                    || !intent.merchantOrderNo().equals(value.merchantOrderNo()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public String requestDigest(String tenant, String scope, AcquireOrderCreateCommand command) {
        // UTF-8 byte-length framing is independent of transport JSON ordering/number serializers.
        var canonical = new StringBuilder();
        for (String field : Arrays.asList(
                "acquire.order-create.v1",
                tenant,
                scope,
                "ORDER",
                command.payeeMerchantId(),
                command.merchantOrderNo(),
                command.productScopeKey(),
                Long.toString(command.amountMinor()),
                command.currency(),
                command.subject(),
                command.requestedDueAt() == null
                        ? null
                        : command.requestedDueAt().toString())) {
            if (field == null) canonical.append("-1:");
            else
                canonical
                        .append(field.getBytes(StandardCharsets.UTF_8).length)
                        .append(':')
                        .append(field);
        }
        return checksum(canonical.toString());
    }

    @Override
    public String encodeCheckout(CheckoutCreateCommand command) {
        return mapper.writeValueAsString(command);
    }

    @Override
    public CheckoutCreateCommand decodeCheckout(OrderCreateIntent intent) {
        try {
            if (!"CHECKOUT".equals(intent.entryMode())) throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(intent.intentPayload(), CheckoutCreateCommand.class);
            if (value == null
                    || !validator.validate(value).isEmpty()
                    || !intent.payeeMerchantId().equals(value.payeeMerchantId())
                    || !intent.merchantOrderNo().equals(value.merchantOrderNo()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public String checkoutDigest(String tenant, String scope, CheckoutCreateCommand command) {
        var canonical = new StringBuilder();
        for (String field : Arrays.asList(
                "acquire.checkout-create.v1",
                tenant,
                scope,
                "CHECKOUT",
                command.payeeMerchantId(),
                command.merchantOrderNo(),
                command.productScopeKey(),
                Long.toString(command.amountMinor()),
                command.currency(),
                command.subject(),
                command.requestedDueAt() == null
                        ? null
                        : command.requestedDueAt().toString(),
                command.returnTargetCode())) {
            if (field == null) canonical.append("-1:");
            else
                canonical
                        .append(field.getBytes(StandardCharsets.UTF_8).length)
                        .append(':')
                        .append(field);
        }
        return checksum(canonical.toString());
    }

    @Override
    public String encodeAdmission(OrderAdmissionDTO admission) {
        var payload = mapper.writeValueAsString(admission);
        if (payload.length() > 262144) throw new CommonException(SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public String encodeDirectPay(io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command) {
        return mapper.writeValueAsString(command);
    }

    @Override
    public io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand decodeDirectPay(OrderCreateIntent intent) {
        try {
            if (!"DIRECT_PAY".equals(intent.entryMode())) throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(intent.intentPayload(), io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand.class);
            if (value == null || !validator.validate(value).isEmpty() || value.firstPaymentAmountMinor() > value.amountMinor()
                    || !value.interactionKind().supports(value.choice().method())
                    || !intent.payeeMerchantId().equals(value.payeeMerchantId()) || !intent.merchantOrderNo().equals(value.merchantOrderNo()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public String directPayDigest(String tenant, String scope, io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand command) {
        var canonical = new StringBuilder();
        for (String field : Arrays.asList("acquire.direct-pay-create.v1", tenant, scope, "DIRECT_PAY", command.payeeMerchantId(),
                command.merchantOrderNo(), command.productScopeKey(), Long.toString(command.amountMinor()), command.currency(), command.subject(),
                command.requestedDueAt() == null ? null : command.requestedDueAt().toString(), command.actionKey(),
                Long.toString(command.firstPaymentAmountMinor()), command.choice().channel().name(), command.choice().method().name(),
                command.interactionKind().name())) {
            if (field == null) canonical.append("-1:");
            else canonical.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        }
        return checksum(canonical.toString());
    }

    @Override
    public OrderAdmissionDTO decodeAdmission(String payload, String checksum) {
        try {
            if (payload == null
                    || payload.length() > 262144
                    || !checksum(payload).equals(checksum)) throw new CommonException(SNAPSHOT_INVALID);
            var value = mapper.readValue(payload, OrderAdmissionDTO.class);
            if (value == null
                    || !validator.validate(value).isEmpty()
                    || value.terms() == null
                    || value.terms().schemaVersion() != 1
                    || value.decision() != io.github.loadup.pay.product.domain.model.AdmissionDecision.ADMITTED)
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException failure) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public String encodeFee(OrderFeePolicy policy) {
        return mapper.writeValueAsString(policy);
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
}
