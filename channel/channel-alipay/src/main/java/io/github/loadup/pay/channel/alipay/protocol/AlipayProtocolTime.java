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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Provider wire time uses GMT+8; business clocks and runtime time zones remain locally configured. */
public final class AlipayProtocolTime {
    private static final ZoneOffset WIRE_ZONE = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private AlipayProtocolTime() {
    }

    public static String requestTimestamp(Clock clock) {
        return FORMAT.format(clock.instant().atOffset(WIRE_ZONE));
    }

    public static String expiration(OffsetDateTime value) {
        return FORMAT.format(value.atZoneSameInstant(WIRE_ZONE));
    }

    public static LocalDateTime businessTime(String wireTime, ZoneId businessZone) {
        var instant = LocalDateTime.parse(wireTime, FORMAT).atOffset(WIRE_ZONE).toInstant();
        return LocalDateTime.ofInstant(instant, businessZone);
    }
}

