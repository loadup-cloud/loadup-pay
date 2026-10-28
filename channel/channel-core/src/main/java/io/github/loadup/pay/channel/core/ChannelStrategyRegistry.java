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

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DUPLICATE_PROVIDER;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * Immutable strategy lookup shared by the payment and notification facades.
 */
final class ChannelStrategyRegistry<S> {
    private final Map<Channel, S> strategies;

    ChannelStrategyRegistry(Collection<S> candidates, Function<S, Channel> channelOf) {
        if (candidates == null || channelOf == null) throw new CommonException(INVALID_ARGUMENT);
        var registered = new EnumMap<Channel, S>(Channel.class);
        for (var candidate : candidates) {
            if (candidate == null) throw new CommonException(INVALID_ARGUMENT);
            var channel = channelOf.apply(candidate);
            if (channel == null) throw new CommonException(INVALID_ARGUMENT);
            if (registered.putIfAbsent(channel, candidate) != null) throw new CommonException(DUPLICATE_PROVIDER);
        }
        strategies = Map.copyOf(registered);
    }

    Optional<S> find(Channel channel) {
        if (channel == null) throw new CommonException(INVALID_ARGUMENT);
        return Optional.ofNullable(strategies.get(channel));
    }

    S require(Channel channel) {
        return find(channel).orElseThrow(() -> new CommonException(UNSUPPORTED_CAPABILITY));
    }
}
