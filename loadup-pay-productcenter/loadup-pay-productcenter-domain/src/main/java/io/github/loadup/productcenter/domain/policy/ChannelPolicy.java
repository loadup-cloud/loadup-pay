/*
 * Copyright (C) 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.productcenter.domain.policy;

import io.github.loadup.components.payment.channel.contract.AccessMode;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record ChannelPolicy(List<Capability> capabilities, String routingProfileRef) {
    public ChannelPolicy {
        PolicyChecks.require(
                capabilities != null && !capabilities.isEmpty() && PolicyChecks.requiredText(routingProfileRef, 128));
        capabilities = List.copyOf(capabilities);
    }

    public record Capability(Channel channel, AccessMode accessMode, Set<PaymentMethod> methods) {
        public Capability {
            PolicyChecks.require(channel != null && accessMode != null && methods != null && !methods.isEmpty());
            methods = Collections.unmodifiableSet(EnumSet.copyOf(methods));
        }
    }
}
