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
package io.github.loadup.pay.acquire.infrastructure.config;

import io.github.loadup.commons.payment.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.Set;

/** Hosted H5 channels must be explicitly enabled after their payment providers are deployed. */
@ConfigurationProperties("loadup.modules.acquire.cashier")
public record CashierProperties(Set<Channel> h5Channels) {
    public CashierProperties { h5Channels = h5Channels == null ? Set.of() : Set.copyOf(h5Channels); }
}
