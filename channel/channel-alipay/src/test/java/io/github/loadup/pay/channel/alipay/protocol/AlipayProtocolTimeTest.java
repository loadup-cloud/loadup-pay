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
package io.github.loadup.pay.channel.alipay.protocol;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlipayProtocolTimeTest {
    @Test
    void formatsWireTimestampInGmtPlusEightRegardlessOfClockZone() {
        var clock = Clock.fixed(Instant.parse("2026-10-23T10:11:12Z"), ZoneId.of("America/Los_Angeles"));
        assertEquals("2026-10-23 18:11:12", AlipayProtocolTime.requestTimestamp(clock));
    }

    @Test
    void convertsWireGmtPlusEightTimeToTheConfiguredBusinessZone() {
        assertEquals(LocalDateTime.parse("2026-10-23T05:11:12"),
                AlipayProtocolTime.businessTime("2026-10-23 18:11:12", ZoneOffset.ofHours(-5)));
        assertEquals(LocalDateTime.parse("2026-10-23T10:11:12"),
                AlipayProtocolTime.businessTime("2026-10-23 18:11:12", ZoneOffset.UTC));
    }
}
