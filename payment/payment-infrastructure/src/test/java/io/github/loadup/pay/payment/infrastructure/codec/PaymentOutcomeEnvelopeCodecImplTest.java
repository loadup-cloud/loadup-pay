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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentOutcomeEnvelopeCodecImplTest {
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final PaymentOutcomeEnvelopeCodecImpl codec = new PaymentOutcomeEnvelopeCodecImpl(mapper);

    EventEnvelope<PaymentOutcomeEventDTO> event(String producer, int version, String aggregate) {
        var data = new PaymentOutcomeEventDTO("payment", "order", "allocation", "operation", "merchant", 1000, "CNY",
                PaymentState.SUCCEEDED, 2, "evidence", false);
        return new EventEnvelope<>("delivery", "evidence", producer, "pay.payment.outcome.limit.v1", version,
                aggregate, 2, OffsetDateTime.parse("2026-10-23T12:00:00+08:00"), data);
    }

    @Test
    void canonicalTypedEnvelopeRoundTrips() {
        var expected = event("payment", 1, "payment");
        assertEquals(expected, codec.decode(mapper.writeValueAsString(expected)));
    }

    @Test
    void producerLabelAndAggregateMustMatchTheTypedPaymentFact() {
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> codec.decode(mapper.writeValueAsString(event("merchant", 1, "payment")))).getResultCode());
        assertThrows(CommonException.class, () -> codec.decode(mapper.writeValueAsString(event("payment", 1, "other-payment"))));
    }

    @Test
    void unsupportedSchemaCannotBeSilentlyInterpretedAsTheCurrentSchema() {
        assertThrows(CommonException.class, () -> codec.decode(mapper.writeValueAsString(event("payment", 2, "payment"))));
    }

    @Test
    void unknownFieldsCannotCarryAnUnvalidatedAlternateFact() {
        String original = mapper.writeValueAsString(event("payment", 1, "payment"));
        String extra = original.substring(0, original.length() - 1) + ",\"overrideVersion\":99}";
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> codec.decode(extra)).getResultCode());
    }
}
