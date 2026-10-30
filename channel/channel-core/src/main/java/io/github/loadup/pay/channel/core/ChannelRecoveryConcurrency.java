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
package io.github.loadup.pay.channel.core;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.components.resilience4j.ResilienceRegistries;
import io.github.resilience4j.bulkhead.Bulkhead;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.RECOVERY_BUSY;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.RECOVERY_CONFIGURATION_INVALID;

/**
 * Shared per-channel recovery admission; it never wraps a newly won first-submission ticket.
 */
public final class ChannelRecoveryConcurrency {
    private final Map<Channel, Bulkhead> limits;

    public ChannelRecoveryConcurrency(ResilienceRegistries registries) {
        Objects.requireNonNull(registries);
        var values = new EnumMap<Channel, Bulkhead>(Channel.class);
        for (var channel : Channel.values()) {
            var limit = registries.bulkheadRegistry().bulkhead(instanceName(channel));
            if (!limit.getBulkheadConfig().getMaxWaitDuration().isZero()) {
                throw new CommonException(RECOVERY_CONFIGURATION_INVALID);
            }
            values.put(channel, limit);
        }
        limits = Map.copyOf(values);
    }

    public static String instanceName(Channel channel) {
        return "pay-channel-recovery-" + channel.name().toLowerCase(Locale.ROOT);
    }

    public <T> T execute(Channel channel, Supplier<T> work) {
        var limit = limits.get(Objects.requireNonNull(channel));
        Objects.requireNonNull(work);
        if (!limit.tryAcquirePermission()) {
            throw new CommonException(RECOVERY_BUSY);
        }
        try {
            return work.get();
        } finally {
            limit.onComplete();
        }
    }
}
