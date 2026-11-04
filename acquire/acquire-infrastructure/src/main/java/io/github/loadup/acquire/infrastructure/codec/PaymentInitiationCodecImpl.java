/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.infrastructure.codec;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.acquire.app.port.OrderCreationCodec;
import io.github.loadup.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.acquire.infrastructure.converter.PaymentIntentSnapshotConverter;
import io.github.loadup.acquire.infrastructure.model.PaymentIntentSnapshot;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.payment.client.command.PaymentExecuteCommand;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PaymentInitiationCodecImpl implements PaymentInitiationCodec {
    private final JsonMapper mapper;
    private final Validator validator;
    private final OrderCreationCodec hashes;
    private final PaymentIntentSnapshotConverter snapshots;

    public PaymentInitiationCodecImpl(
            Validator validator, OrderCreationCodec hashes, PaymentIntentSnapshotConverter snapshots) {
        this.mapper = JsonUtil.strictProtocol(JsonMapper.builder()).build();
        this.validator = validator;
        this.hashes = hashes;
        this.snapshots = snapshots;
    }

    @Override
    public String encode(PaymentExecuteCommand command) {
        try {
            if (command == null || !validator.validate(command).isEmpty()) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            String payload = mapper.writeValueAsString(snapshots.toSnapshot(command));
            if (payload.getBytes(StandardCharsets.UTF_8).length > 262144) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return payload;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public PaymentExecuteCommand decode(StoredPaymentAllocation stored) {
        try {
            if (stored.intentPayload().getBytes(StandardCharsets.UTF_8).length > 262144
                    || !hashes.checksum(stored.intentPayload()).equals(stored.payloadHash())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var value = mapper.readValue(stored.intentPayload(), PaymentIntentSnapshot.class);
            var allocation = stored.allocation();
            if (value == null
                    || !allocation.id().equals(value.allocationId())
                    || !allocation.orderId().equals(value.orderId())
                    || !allocation.paymentId().equals(value.paymentId())
                    || !allocation.requestDigest().equals(value.requestDigest())
                    || allocation.amount().getCent() != value.amountMinor()
                    || !allocation.amount().getCurrencyValue().equals(value.currency())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            // The owner-local format permits staged input references, without depending on current Command validation.
            return snapshots.toCommand(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
