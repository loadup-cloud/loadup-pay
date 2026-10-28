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
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class RefundProjectionEnvelopeCodecImpl implements RefundProjectionEnvelopeCodec {
    private final JsonMapper json;

    public RefundProjectionEnvelopeCodecImpl(JsonMapper json) {
        this.json = json.rebuild().enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    @Override
    public EventEnvelope<RefundSnapshotEventDTO> child(String payload) {
        try {
            var event = decode(payload, new TypeReference<EventEnvelope<RefundSnapshotEventDTO>>() {
            });
            event.data().requireEnvelope(event);
            return event;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public EventEnvelope<OrderRefundSummaryEventDTO> summary(String payload) {
        return monetary(payload, OrderRefundSummaryEventDTO.REALTIME_TYPE);
    }

    @Override
    public EventEnvelope<OrderRefundSummaryEventDTO> fee(String payload) {
        return monetary(payload, OrderRefundSummaryEventDTO.FEE_TYPE);
    }

    private EventEnvelope<OrderRefundSummaryEventDTO> monetary(String payload, String type) {
        try {
            var event = decode(payload, new TypeReference<EventEnvelope<OrderRefundSummaryEventDTO>>() {
            });
            event.data().requireEnvelope(event);
            event.requireSource(type, "payment", event.data().orderId(), event.data().refundSummaryVersion());
            return event;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private <T> T decode(String payload, TypeReference<T> type) {
        if (payload == null || payload.length() > 65536) throw new CommonException(SNAPSHOT_INVALID);
        var event = json.readValue(payload, type);
        if (event == null) throw new CommonException(SNAPSHOT_INVALID);
        return event;
    }

    @Override
    public EventEnvelope<RefundOutcomeEventDTO> limit(String payload) {
        try {
            var event = decode(payload, new TypeReference<EventEnvelope<RefundOutcomeEventDTO>>() {
            });
            event.data().requireEnvelope(event);
            if (event.data().state() != RefundState.SUCCEEDED) throw new CommonException(SNAPSHOT_INVALID);
            return event;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public EventEnvelope<RefundMerchantEventDTO> merchant(String payload) {
        try {
            var event = decode(payload, new TypeReference<EventEnvelope<RefundMerchantEventDTO>>() {
            });
            event.data().requireEnvelope(event);
            return event;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
