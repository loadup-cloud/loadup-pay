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
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.event.PaymentSnapshotEventDTO;
import io.github.loadup.pay.payment.app.port.PaymentSnapshotEnvelopeCodec;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class PaymentSnapshotEnvelopeCodecImpl implements PaymentSnapshotEnvelopeCodec {
    private final JsonMapper mapper;
    public PaymentSnapshotEnvelopeCodecImpl(JsonMapper mapper) { this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build(); }
    @Override public PayEventEnvelope<PaymentSnapshotEventDTO> decode(String payload) {
        try {
            if (payload == null || payload.length() > 65536) throw new CommonException(SNAPSHOT_INVALID);
            var event = mapper.readValue(payload, new TypeReference<PayEventEnvelope<PaymentSnapshotEventDTO>>() {});
            if (event == null) throw new CommonException(SNAPSHOT_INVALID);
            event.data().requireEnvelope(event); return event;
        } catch (RuntimeException invalid) { throw new CommonException(SNAPSHOT_INVALID); }
    }
}
