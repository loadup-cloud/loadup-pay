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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChannelStrategyRegistryTest {
    @Test
    void routesByChannelAndKeepsAnImmutableRegistrationSnapshot() {
        var alipay = new Strategy(Channel.ALIPAY);
        var wechat = new Strategy(Channel.WECHAT);
        var candidates = new ArrayList<>(List.of(wechat, alipay));
        var registry = new ChannelStrategyRegistry<>(candidates, Strategy::channel);
        candidates.clear();

        assertSame(alipay, registry.require(Channel.ALIPAY));
        assertSame(wechat, registry.require(Channel.WECHAT));
    }

    @Test
    void duplicateStrategiesFailRegistrationInsteadOfDependingOnCollectionOrder() {
        assertThrows(CommonException.class, () -> new ChannelStrategyRegistry<>(
                List.of(new Strategy(Channel.ALIPAY), new Strategy(Channel.ALIPAY)), Strategy::channel));
    }

    @Test
    void missingChannelHasNoDefaultOrFallbackStrategy() {
        var registry = new ChannelStrategyRegistry<>(List.of(new Strategy(Channel.WECHAT)), Strategy::channel);

        assertTrue(registry.find(Channel.ALIPAY).isEmpty());
        assertThrows(CommonException.class, () -> registry.require(Channel.ALIPAY));
        assertThrows(CommonException.class, () -> registry.require(null));
    }

    @Test
    void registrationRejectsNullCollectionsStrategiesAndChannelKeys() {
        assertThrows(CommonException.class, () -> new ChannelStrategyRegistry<Strategy>(null, Strategy::channel));
        assertThrows(CommonException.class, () -> new ChannelStrategyRegistry<Strategy>(List.of(), null));
        assertThrows(CommonException.class, () -> new ChannelStrategyRegistry<>(Collections.<Strategy>singletonList(null), Strategy::channel));
        assertThrows(CommonException.class, () -> new ChannelStrategyRegistry<>(List.of(new Strategy(null)), Strategy::channel));
    }

    private record Strategy(Channel channel) {}
}
