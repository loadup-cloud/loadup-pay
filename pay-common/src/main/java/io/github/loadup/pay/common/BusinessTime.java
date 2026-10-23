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
package io.github.loadup.pay.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

public final class BusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessTime() {
    }

    public static LocalDateTime local(OffsetDateTime value) {
        return Objects.requireNonNull(value, "value").atZoneSameInstant(ZONE).toLocalDateTime();
    }

    public static OffsetDateTime external(LocalDateTime value) {
        return Objects.requireNonNull(value, "value").atZone(ZONE).toOffsetDateTime();
    }
}
