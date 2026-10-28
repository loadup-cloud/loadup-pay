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
package io.github.loadup.pay.realtime.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.app.port.TradeProjectionCodec;
import io.github.loadup.pay.realtime.domain.model.SourceVersion;
import io.github.loadup.pay.realtime.domain.model.TradeOrderProjection;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class TradeProjectionCodecImpl implements TradeProjectionCodec {
    private final JsonMapper mapper;
    private final TradeProjectionConverter converter;

    public TradeProjectionCodecImpl(JsonMapper mapper, TradeProjectionConverter converter) {
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.converter = converter;
    }

    @Override
    public String encode(TradeOrderProjection value) {
        return bounded(mapper.writeValueAsString(converter.toEvent(value)));
    }

    @Override
    public TradeOrderProjection order(String payload, String expectedHash, LocalDateTime snapshotAt) {
        var event = decode(payload, expectedHash, OrderSnapshotEventDTO.class);
        return converter.toOrder(event, new SourceVersion("acquire", "AcquireOrder", event.orderId(),
                event.sourceVersion()), snapshotAt);
    }

    @Override
    public String encode(TradePaymentProjection value) {
        return bounded(mapper.writeValueAsString(converter.toEvent(value)));
    }

    @Override
    public TradePaymentProjection payment(String payload, String expectedHash, LocalDateTime snapshotAt) {
        var event = decode(payload, expectedHash, PaymentSnapshotEventDTO.class);
        return converter.toPayment(event, new SourceVersion("payment", "PaymentOrder", event.paymentId(),
                event.sourceVersion()), snapshotAt);
    }

    @Override
    public String encode(TradeRefundProjection value) {
        return bounded(mapper.writeValueAsString(converter.toEvent(value)));
    }

    @Override
    public TradeRefundProjection refund(String payload, String expectedHash, LocalDateTime snapshotAt) {
        var event = decode(payload, expectedHash, RefundSnapshotEventDTO.class);
        return converter.toRefund(event, new SourceVersion("payment", "RefundOrder", event.refundId(),
                event.sourceVersion()), snapshotAt);
    }

    @Override
    public String encode(TradeRefundSummary value) {
        return bounded(mapper.writeValueAsString(converter.toEvent(value)));
    }

    @Override
    public TradeRefundSummary summary(String payload, String expectedHash, LocalDateTime snapshotAt) {
        var event = decode(payload, expectedHash, OrderRefundSummaryEventDTO.class);
        return converter.toSummary(event, new SourceVersion("payment", "OrderRefundSummary", event.orderId(),
                event.refundSummaryVersion()), snapshotAt);
    }

    private <T> T decode(String payload, String expectedHash, Class<T> type) {
        try {
            bounded(payload);
            var value = mapper.readValue(payload, type);
            if (value == null || !hash(mapper.writeValueAsString(value)).equals(expectedHash))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private String bounded(String payload) {
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > 1048576)
            throw new CommonException(SNAPSHOT_INVALID);
        return payload;
    }

    @Override
    public String hash(String payload) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    @Override
    public String requestDigest(String tenant, String service, EventEnvelope<?> event, String factsHash) {
        var data = new StringBuilder();
        for (var field : List.of("realtime-delivery-v1", tenant, service, event.deliveryId(), event.businessEventId(), event.producer(),
                event.eventType(), Integer.toString(event.schemaVersion()), event.aggregateId(), Long.toString(event.aggregateVersion()),
                event.occurredAt().toString(), factsHash))
            data.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        return hash(data.toString());
    }
}
