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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * The +08:00 boundary is the domestic channel statement protocol, never a global/JVM time-zone override.
 */
public record PaymentCaptureScope(Channel channel, String accountScopeHash, LocalDate billDate, String statementProfile,
                                  String systemZone) {
    public static final String WECHAT_ALL = "wechat-trade-all-20260116";

    public PaymentCaptureScope {
        require(channel == Channel.WECHAT && accountScopeHash != null && accountScopeHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require(billDate != null && billDate.getYear() >= 1970 && billDate.getYear() <= 9998 && WECHAT_ALL.equals(statementProfile), INVALID_ARGUMENT);
        require(systemZone != null && systemZone.equals(ZoneId.of(systemZone).getId()), INVALID_ARGUMENT);
    }

    public LocalDateTime windowStart() {
        return localBoundary(billDate);
    }

    public LocalDateTime windowEnd() {
        return localBoundary(billDate.plusDays(1));
    }

    private LocalDateTime localBoundary(LocalDate date) {
        return date.atStartOfDay().atOffset(ZoneOffset.ofHours(8)).atZoneSameInstant(ZoneId.of(systemZone)).toLocalDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
