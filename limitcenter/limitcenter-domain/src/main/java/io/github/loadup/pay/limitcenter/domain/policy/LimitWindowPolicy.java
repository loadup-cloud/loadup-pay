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
package io.github.loadup.pay.limitcenter.domain.policy;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.limitcenter.domain.model.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

public final class LimitWindowPolicy {
    public WindowBounds at(LimitWindowType type, LocalDateTime initiatedAt, DayOfWeek weekStart) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(initiatedAt);
        Objects.requireNonNull(weekStart);
        LocalDate day = initiatedAt.toLocalDate();
        return switch (type) {
            case DAY -> new WindowBounds(day.atStartOfDay(), day.plusDays(1).atStartOfDay());
            case WEEK -> {
                LocalDate start = day.with(TemporalAdjusters.previousOrSame(weekStart));
                yield new WindowBounds(start.atStartOfDay(), start.plusWeeks(1).atStartOfDay());
            }
            case MONTH -> {
                LocalDate start = day.withDayOfMonth(1);
                yield new WindowBounds(start.atStartOfDay(), start.plusMonths(1).atStartOfDay());
            }
            case LIFETIME -> new WindowBounds(LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 0, 0));
        };
    }

    public WindowBounds at(LimitWindowType type, OffsetDateTime initiatedAt, DayOfWeek weekStart) {
        return at(type, BusinessTime.local(initiatedAt), weekStart);
    }
}
