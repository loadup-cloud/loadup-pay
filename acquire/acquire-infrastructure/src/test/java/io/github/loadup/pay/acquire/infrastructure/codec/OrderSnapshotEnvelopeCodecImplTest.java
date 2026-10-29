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
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSnapshotEnvelopeCodecImplTest {
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final OrderSnapshotEnvelopeCodecImpl codec = new OrderSnapshotEnvelopeCodecImpl(mapper);
    final OffsetDateTime time = OffsetDateTime.parse("2026-10-23T12:00:00+08:00");

    EventEnvelope<OrderSnapshotEventDTO> event(String producer, long version) {
        return new EventEnvelope<>("delivery", "change", producer, "pay.acquire.snapshot.realtime.v1", 1, "order", version, time,
                new OrderSnapshotEventDTO("order", "merchant", "M001", 10000, "CNY", 4000, 0, AcquireOrderState.PENDING, time, time, 2));
    }

    @Test
    void roundTripsACompleteSnapshotWithOriginalOffsetTimes() {
        var source = event("acquire", 2);
        assertEquals(source, codec.decode(mapper.writeValueAsString(source)));
    }

    @Test
    void wrongProducerOrEnvelopeVersionCannotChangeSourceIdentity() {
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> codec.decode(mapper.writeValueAsString(event("payment", 2)))).getResultCode());
        assertThrows(CommonException.class, () -> codec.decode(mapper.writeValueAsString(event("acquire", 3))));
    }

    @Test
    void unknownFieldsAndOversizedOrInvalidJsonAreRejectedWithoutExposingPayload() {
        var json = mapper.writeValueAsString(event("acquire", 2));
        assertThrows(CommonException.class, () -> codec.decode(json.substring(0, json.length() - 1) + ",\"unknown\":true}"));
        assertThrows(CommonException.class, () -> codec.decode("x".repeat(65537)));
        assertThrows(CommonException.class, () -> codec.decode("{"));
    }
}
