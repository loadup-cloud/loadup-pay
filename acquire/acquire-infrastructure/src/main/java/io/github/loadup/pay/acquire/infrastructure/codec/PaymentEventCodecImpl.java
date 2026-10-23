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

import io.github.loadup.pay.acquire.app.port.PaymentEventCodec;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
public class PaymentEventCodecImpl implements PaymentEventCodec {
    @Override
    public String digest(String tenant, String service, EventEnvelope<PaymentOutcomeEventDTO> event) {
        var value = event.data();
        var framed = new StringBuilder();
        for (String field : List.of("acquire-payment-event-v1", tenant, service, event.deliveryId(), event.businessEventId(),
                event.producer(), event.eventType(), Integer.toString(event.schemaVersion()), event.aggregateId(),
                Long.toString(event.aggregateVersion()), event.occurredAt().toString(), value.paymentId(), value.orderId(),
                value.allocationId(), value.operationId(), value.payeeMerchantId(), Long.toString(value.amountMinor()),
                value.currency(), value.state().name(), Long.toString(value.paymentVersion()), value.evidenceId(),
                Boolean.toString(value.executionFenced())))
            framed.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(framed.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
