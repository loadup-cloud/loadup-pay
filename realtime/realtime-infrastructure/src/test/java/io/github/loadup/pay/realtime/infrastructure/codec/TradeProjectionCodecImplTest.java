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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.realtime.app.converter.TradeProjectionConverter;
import io.github.loadup.pay.realtime.domain.model.SourceVersion;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TradeProjectionCodecImplTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TradeProjectionConverter.class.getPackageName());
    final TradeProjectionCodecImpl codec = new TradeProjectionCodecImpl(JsonUtil.customize(JsonMapper.builder()).build(),
            context.getBean(TradeProjectionConverter.class));
    final LocalDateTime at = LocalDateTime.of(2026, 10, 23, 12, 0);

    TradePaymentProjection payment(LocalDateTime snapshotAt) {
        return new TradePaymentProjection("payment", "order", "merchant", DomainChecks.cnyAmount(1000, "CNY"), "SUCCEEDED",
                Channel.WECHAT, AccessMode.DIRECT, at.minusMinutes(1), at, new SourceVersion("payment", "PaymentOrder", "payment", 2), snapshotAt);
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void transportArrivalTimeDoesNotChangeTheSourceContentHash() {
        assertEquals(codec.hash(codec.encode(payment(at))), codec.hash(codec.encode(payment(at.plusHours(1)))));
    }

    @Test
    void typedPayloadRoundTripsWithAnIndependentLocalProjectionTime() {
        String payload = codec.encode(payment(at));
        var restored = codec.payment(payload, codec.hash(payload), at.plusHours(1));
        assertEquals(at.plusHours(1), restored.snapshotAt());
        assertEquals(1000, restored.amount().getCent());
        assertEquals(at, restored.succeededAt());
    }

    @Test
    void alteredAmountsOrUnknownFieldsCannotBeReadAsTheSameSourceVersion() {
        String payload = codec.encode(payment(at));
        String hash = codec.hash(payload);
        assertThrows(CommonException.class, () -> codec.payment(payload.replace("1000", "999"), hash, at));
        String extra = payload.substring(0, payload.length() - 1) + ",\"trustedOverride\":true}";
        assertThrows(CommonException.class, () -> codec.payment(extra, hash, at));
    }

    @Test
    void whitespaceDoesNotBecomeANewFinancialFact() {
        String payload = codec.encode(payment(at));
        assertEquals(1000, codec.payment(" \n" + payload + "\n ", codec.hash(payload), at).amount().getCent());
    }
}
